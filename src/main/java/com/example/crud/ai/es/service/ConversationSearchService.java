package com.example.crud.ai.es.service;

import com.example.crud.ai.es.model.EsMessageDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch 기반 대화 검색 및 분석 서비스
 * Spring Boot 3.x / Spring Data Elasticsearch 5.x 호환 버전
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 사용자의 과거 대화에서 관련 컨텍스트 검색
     */
    public List<String> searchRelatedContext(Long conversationId, String query, int size) {
        // Criteria API 사용 (Spring Data ES 5.x)
        Criteria criteria = new Criteria("conversationId").is(conversationId)
                .and(new Criteria("content").matches(query));

        Query searchQuery = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(0, size));

        SearchHits<EsMessageDoc> hits = elasticsearchOperations.search(
                searchQuery, EsMessageDoc.class
        );

        return hits.stream()
                .map(SearchHit::getContent)
                .map(EsMessageDoc::getContent)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 대화 패턴 분석
     * 주의: Spring Data ES 5.x에서는 Aggregation이 제한적이므로
     * 복잡한 집계는 ElasticsearchClient를 직접 사용하거나
     * 간단한 쿼리 여러개로 분리해서 처리
     */
    public Map<String, Object> analyzeUserConversationPatterns(Long userId) {
        Map<String, Object> patterns = new HashMap<>();

        try {
            // 1. 최근 메시지들 조회
            List<EsMessageDoc> recentMessages = getRecentMessages(userId, 100);

            // 2. 시간대별 활동 패턴
            Map<Integer, Long> hourlyActivity = analyzeHourlyActivity(recentMessages);
            patterns.put("hourlyActivity", hourlyActivity);

            // 3. 대화 주제 분류
            Map<String, Long> topics = classifyTopics(recentMessages);
            patterns.put("topics", topics);

            // 4. 감정 분석 결과
            Map<String, Double> sentiments = analyzeSentimentsFromMessages(recentMessages);
            patterns.put("sentiments", sentiments);

            return patterns;

        } catch (Exception e) {
            log.error("대화 패턴 분석 실패: userId={}", userId, e);
            return patterns;
        }
    }

    /**
     * 유사한 대화를 한 다른 사용자 찾기
     */
    public List<Long> findSimilarUsers(Long userId, int limit) {
        try {
            // 사용자의 최근 키워드 추출
            List<String> userKeywords = extractUserKeywords(userId, 30);

            if (userKeywords.isEmpty()) {
                return new ArrayList<>();
            }

            // 키워드를 포함하는 다른 사용자들의 메시지 검색
            Set<Long> similarUsers = new HashSet<>();

            for (String keyword : userKeywords.subList(0, Math.min(5, userKeywords.size()))) {
                Criteria criteria = new Criteria("content").contains(keyword)
                        .and(new Criteria("userId").not().is(userId));

                Query query = new CriteriaQuery(criteria)
                        .setPageable(PageRequest.of(0, 50));

                SearchHits<EsMessageDoc> hits = elasticsearchOperations.search(
                        query, EsMessageDoc.class
                );

                hits.forEach(hit -> {
                    EsMessageDoc doc = hit.getContent();
                    // userId 필드가 있다고 가정 (없으면 conversationId로부터 추출)
                    if (doc.getConversationId() != null) {
                        // 실제로는 conversationId -> userId 매핑 필요
                        similarUsers.add(doc.getConversationId() % 1000); // 임시
                    }
                });
            }

            return similarUsers.stream()
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("유사 사용자 검색 실패: userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 실시간 트렌딩 키워드 추출
     */
    public List<String> getTrendingKeywords(int hours, int size) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        // 최근 시간 내의 메시지들 조회
        Criteria criteria = new Criteria("timestamp").greaterThanEqual(since);
        Query query = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(0, 1000));

        SearchHits<EsMessageDoc> hits = elasticsearchOperations.search(
                query, EsMessageDoc.class
        );

        // 키워드 빈도 계산
        Map<String, Integer> keywordFrequency = new HashMap<>();

        hits.forEach(hit -> {
            String content = hit.getContent().getContent();
            List<String> keywords = extractKeywordsFromContent(content);
            keywords.forEach(keyword ->
                    keywordFrequency.merge(keyword, 1, Integer::sum)
            );
        });

        // 상위 키워드 반환
        return keywordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(size)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 대화 기반 상품 추천을 위한 컨텍스트 구축
     */
    public String buildRecommendationContext(Long conversationId, int messageCount) {
        Criteria criteria = new Criteria("conversationId").is(conversationId);
        Query query = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(0, messageCount));

        SearchHits<EsMessageDoc> hits = elasticsearchOperations.search(
                query, EsMessageDoc.class
        );

        return hits.stream()
                .map(hit -> hit.getContent().getContent())
                .collect(Collectors.joining(" "));
    }

    // === 헬퍼 메서드들 ===

    private List<EsMessageDoc> getRecentMessages(Long userId, int limit) {
        // userId 필드가 없다면 conversationId로 대체
        Criteria criteria = new Criteria("type").is("USER");
        Query query = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(0, limit));

        SearchHits<EsMessageDoc> hits = elasticsearchOperations.search(
                query, EsMessageDoc.class
        );

        return hits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private Map<Integer, Long> analyzeHourlyActivity(List<EsMessageDoc> messages) {
        Map<Integer, Long> hourlyActivity = new HashMap<>();

        // 0-23시 초기화
        for (int i = 0; i < 24; i++) {
            hourlyActivity.put(i, 0L);
        }

        // 메시지별 시간대 집계
        messages.forEach(msg -> {
            if (msg.getTimestamp() != null) {
                int hour = msg.getTimestamp()
                        .atZone(java.time.ZoneId.systemDefault())
                        .getHour();
                hourlyActivity.merge(hour, 1L, Long::sum);
            }
        });

        return hourlyActivity;
    }

    private Map<String, Long> classifyTopics(List<EsMessageDoc> messages) {
        Map<String, Long> topics = new HashMap<>();

        // 카테고리 키워드 매핑
        Map<String, List<String>> categoryKeywords = Map.of(
                "의류", Arrays.asList("옷", "셔츠", "바지", "원피스", "자켓", "코트", "니트"),
                "가방", Arrays.asList("가방", "백팩", "크로스백", "토트백", "클러치"),
                "신발", Arrays.asList("신발", "운동화", "구두", "부츠", "샌들", "슬리퍼"),
                "악세서리", Arrays.asList("목걸이", "귀걸이", "반지", "팔찌", "모자", "스카프"),
                "스타일", Arrays.asList("캐주얼", "포멀", "스포티", "빈티지", "모던", "클래식")
        );

        // 메시지 분석
        messages.forEach(msg -> {
            String content = msg.getContent().toLowerCase();
            categoryKeywords.forEach((category, keywords) -> {
                for (String keyword : keywords) {
                    if (content.contains(keyword)) {
                        topics.merge(category, 1L, Long::sum);
                        break;
                    }
                }
            });
        });

        return topics;
    }

    private Map<String, Double> analyzeSentimentsFromMessages(List<EsMessageDoc> messages) {
        Map<String, Double> sentiments = new HashMap<>();
        sentiments.put("positive", 0.0);
        sentiments.put("neutral", 0.0);
        sentiments.put("negative", 0.0);

        if (messages.isEmpty()) {
            return sentiments;
        }

        // 감정 키워드 분석
        Map<String, List<String>> sentimentKeywords = Map.of(
                "positive", Arrays.asList("좋아", "예뻐", "만족", "최고", "훌륭", "추천", "감사"),
                "negative", Arrays.asList("싫어", "별로", "실망", "최악", "불만", "비싸", "아쉬워"),
                "neutral", Arrays.asList("그냥", "보통", "평범", "일반적", "무난")
        );

        messages.forEach(msg -> {
            String content = msg.getContent().toLowerCase();
            String detectedSentiment = "neutral";

            for (Map.Entry<String, List<String>> entry : sentimentKeywords.entrySet()) {
                for (String keyword : entry.getValue()) {
                    if (content.contains(keyword)) {
                        detectedSentiment = entry.getKey();
                        break;
                    }
                }
            }

            sentiments.merge(detectedSentiment, 1.0, Double::sum);
        });

        // 백분율로 변환
        double total = messages.size();
        sentiments.replaceAll((k, v) -> (v / total) * 100);

        return sentiments;
    }

    private List<String> extractUserKeywords(Long userId, int days) {
        // 최근 메시지에서 키워드 추출
        List<EsMessageDoc> messages = getRecentMessages(userId, 100);

        Map<String, Integer> wordFrequency = new HashMap<>();

        messages.forEach(msg -> {
            List<String> keywords = extractKeywordsFromContent(msg.getContent());
            keywords.forEach(keyword ->
                    wordFrequency.merge(keyword, 1, Integer::sum)
            );
        });

        // 빈도순 정렬
        return wordFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(30)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> extractKeywordsFromContent(String content) {
        List<String> keywords = new ArrayList<>();

        // 간단한 키워드 추출
        String cleanContent = content.toLowerCase()
                .replaceAll("[^가-힣a-z0-9\\s]", "");

        String[] words = cleanContent.split("\\s+");

        Set<String> stopWords = Set.of(
                "이", "그", "저", "것", "수", "등", "및", "을", "를", "에", "의", "가",
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for"
        );

        for (String word : words) {
            if (word.length() > 1 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }
}