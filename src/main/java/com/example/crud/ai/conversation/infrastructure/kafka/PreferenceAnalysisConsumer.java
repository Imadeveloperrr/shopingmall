package com.example.crud.ai.conversation.infrastructure.kafka;

import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.recommendation.domain.dto.ChatMessage;
import com.example.crud.common.utility.Json;
import com.example.crud.entity.Member;
import com.example.crud.enums.MessageType;
import com.example.crud.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 실시간 사용자 선호도 분석 Consumer
 * - 사용자 메시지를 분석하여 선호도 업데이트
 * - 분석 결과를 Redis에 캐싱
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreferenceAnalysisConsumer {

    private final UserPreferenceRepository prefRepo;
    private final MemberRepository memberRepo;
    private final ChatGptServiceLite gptService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFERENCE_CACHE_PREFIX = "user:preference:";
    private static final String ANALYSIS_LOCK_PREFIX = "analysis:lock:";

    @KafkaListener(topics = "conv-msg-created", groupId = "preference-analysis")
    @Transactional
    public void analyzeUserPreference(String json) {
        try {
            MsgCreatedPayload payload = Json.decode(json, MsgCreatedPayload.class);

            // USER 메시지만 분석
            if (payload.type() != MessageType.USER) {
                return;
            }

            // 분산 환경에서 중복 처리 방지
            String lockKey = ANALYSIS_LOCK_PREFIX + payload.messageId();
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofMinutes(5));

            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.debug("이미 처리 중인 메시지: {}", payload.messageId());
                return;
            }

            try {
                analyzeAndUpdatePreference(payload);
            } finally {
                redisTemplate.delete(lockKey);
            }

        } catch (Exception e) {
            log.error("선호도 분석 실패: {}", e.getMessage(), e);
        }
    }

    private void analyzeAndUpdatePreference(MsgCreatedPayload payload) {
        // 회원 정보 조회
        Member member = memberRepo.findByConversationId(payload.conversationId())
                .orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다"));

        // GPT를 통한 의도 분석
        String analysisPrompt = """
            사용자 메시지: "%s"
            
            이 메시지에서 추출할 수 있는 쇼핑 선호도를 분석해주세요.
            다음 형식의 JSON으로 응답해주세요:
            {
                "categories": ["관심 카테고리들"],
                "styles": ["선호 스타일들"],
                "colors": ["선호 색상들"],
                "priceRange": {"min": 0, "max": 0},
                "brands": ["선호 브랜드들"],
                "keywords": ["키워드들"],
                "sentiment": "positive/neutral/negative"
            }
            """.formatted(payload.content());

        String analysisResult = gptService.completion(
                List.of(new ChatMessage("user", analysisPrompt)),
                analysisPrompt
        ).block(Duration.ofSeconds(5));

        if (analysisResult == null || analysisResult.isEmpty()) {
            return;
        }

        // 기존 선호도와 병합
        UserPreference preference = prefRepo.findByMember_Number(member.getNumber())
                .orElseGet(() -> UserPreference.builder()
                        .member(member)
                        .preferences("{}")
                        .lastUpdated(LocalDateTime.now())
                        .build());

        String mergedPreferences = mergePreferences(
                preference.getPreferences(),
                analysisResult,
                payload.content()
        );

        preference.setPreferences(mergedPreferences);
        preference.setLastUpdated(LocalDateTime.now());

        UserPreference saved = prefRepo.save(preference);

        // Redis에 캐싱 (24시간)
        String cacheKey = PREFERENCE_CACHE_PREFIX + member.getNumber();
        redisTemplate.opsForValue().set(
                cacheKey,
                saved.getPreferences(),
                24,
                TimeUnit.HOURS
        );

        log.info("사용자 {} 선호도 분석 완료", member.getNumber());
    }

    private String mergePreferences(String existing, String newAnalysis, String originalMessage) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            // 기존 선호도 파싱
            Map<String, Object> existingPrefs = existing.isEmpty() ?
                    new HashMap<>() : mapper.readValue(existing, Map.class);

            // 새 분석 결과 파싱
            Map<String, Object> newPrefs = mapper.readValue(newAnalysis, Map.class);

            // 병합된 선호도
            Map<String, Object> merged = new HashMap<>();

            // 1. 카테고리 병합 (가중치 기반)
            List<String> existingCategories = (List<String>) existingPrefs.getOrDefault("categories", new ArrayList<>());
            List<String> newCategories = (List<String>) newPrefs.getOrDefault("categories", new ArrayList<>());
            Map<String, Double> categoryWeights = mergeCategoriesWithWeight(existingCategories, newCategories);
            merged.put("categories", getTopCategories(categoryWeights, 5));

            // 2. 스타일 병합 (빈도 기반)
            List<String> existingStyles = (List<String>) existingPrefs.getOrDefault("styles", new ArrayList<>());
            List<String> newStyles = (List<String>) newPrefs.getOrDefault("styles", new ArrayList<>());
            Map<String, Integer> styleFrequency = mergeWithFrequency(existingStyles, newStyles);
            merged.put("styles", getTopItems(styleFrequency, 5));

            // 3. 색상 병합 (최근성 우선)
            List<String> existingColors = (List<String>) existingPrefs.getOrDefault("colors", new ArrayList<>());
            List<String> newColors = (List<String>) newPrefs.getOrDefault("colors", new ArrayList<>());
            merged.put("colors", mergeWithRecency(existingColors, newColors, 0.7));

            // 4. 가격대 병합 (평균 및 범위 확장)
            Map<String, Object> priceRange = mergePriceRange(
                    (Map<String, Object>) existingPrefs.get("priceRange"),
                    (Map<String, Object>) newPrefs.get("priceRange")
            );
            merged.put("priceRange", priceRange);

            // 5. 브랜드 병합
            List<String> existingBrands = (List<String>) existingPrefs.getOrDefault("brands", new ArrayList<>());
            List<String> newBrands = (List<String>) newPrefs.getOrDefault("brands", new ArrayList<>());
            merged.put("brands", mergeUniqueLists(existingBrands, newBrands, 10));

            // 6. 키워드 병합 (TF-IDF 기반)
            List<String> existingKeywords = (List<String>) existingPrefs.getOrDefault("keywords", new ArrayList<>());
            List<String> newKeywords = (List<String>) newPrefs.getOrDefault("keywords", new ArrayList<>());
            merged.put("keywords", mergeKeywordsWithTFIDF(existingKeywords, newKeywords));

            // 7. 감정 분석 병합 (가중 평균)
            String sentiment = mergeSentiment(
                    (String) existingPrefs.get("sentiment"),
                    (String) newPrefs.get("sentiment")
            );
            merged.put("sentiment", sentiment);

            // 8. 메타데이터 추가
            merged.put("lastUpdated", LocalDateTime.now().toString());
            merged.put("updateCount", ((Integer) existingPrefs.getOrDefault("updateCount", 0)) + 1);
            merged.put("confidence", calculateConfidence(merged));

            // 9. 대화 이력 추가 (최근 10개)
            List<String> history = (List<String>) existingPrefs.getOrDefault("messageHistory", new ArrayList<>());
            history.add(0, originalMessage);
            if (history.size() > 10) {
                history = history.subList(0, 10);
            }
            merged.put("messageHistory", history);

            return mapper.writeValueAsString(merged);

        } catch (Exception e) {
            log.error("선호도 병합 실패", e);
            return existing;
        }
    }

    private Map<String, Double> mergeCategoriesWithWeight(List<String> existing, List<String> newItems) {
        Map<String, Double> weights = new HashMap<>();

        // 기존 카테고리 (시간 감쇠 적용)
        for (int i = 0; i < existing.size(); i++) {
            String category = existing.get(i);
            double weight = 0.5 * Math.pow(0.9, i); // 오래된 것일수록 가중치 감소
            weights.merge(category, weight, Double::sum);
        }

        // 새 카테고리 (높은 가중치)
        for (String category : newItems) {
            weights.merge(category, 1.0, Double::sum);
        }

        return weights;
    }

    private List<String> getTopCategories(Map<String, Double> weights, int limit) {
        return weights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Map<String, Integer> mergeWithFrequency(List<String> existing, List<String> newItems) {
        Map<String, Integer> frequency = new HashMap<>();
        existing.forEach(item -> frequency.merge(item, 1, Integer::sum));
        newItems.forEach(item -> frequency.merge(item, 2, Integer::sum)); // 새 항목은 2배 가중치
        return frequency;
    }

    private List<String> getTopItems(Map<String, Integer> frequency, int limit) {
        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> mergeWithRecency(List<String> existing, List<String> newItems, double newWeight) {
        Set<String> merged = new LinkedHashSet<>();

        // 새 항목 우선 추가
        newItems.forEach(merged::add);

        // 기존 항목 중 일부만 유지
        int keepCount = (int) ((1 - newWeight) * existing.size());
        existing.stream().limit(keepCount).forEach(merged::add);

        return new ArrayList<>(merged);
    }

    private Map<String, Object> mergePriceRange(Map<String, Object> existing, Map<String, Object> newRange) {
        Map<String, Object> merged = new HashMap<>();

        if (existing == null && newRange == null) {
            merged.put("min", 0);
            merged.put("max", Integer.MAX_VALUE);
            return merged;
        }

        int existingMin = existing != null ? (Integer) existing.getOrDefault("min", 0) : 0;
        int existingMax = existing != null ? (Integer) existing.getOrDefault("max", Integer.MAX_VALUE) : Integer.MAX_VALUE;
        int newMin = newRange != null ? (Integer) newRange.getOrDefault("min", 0) : 0;
        int newMax = newRange != null ? (Integer) newRange.getOrDefault("max", Integer.MAX_VALUE) : Integer.MAX_VALUE;

        // 가중 평균으로 범위 계산
        merged.put("min", (int) (existingMin * 0.3 + newMin * 0.7));
        merged.put("max", (int) (existingMax * 0.3 + newMax * 0.7));

        // 평균 가격도 저장
        merged.put("avgPrice", ((int) merged.get("min") + (int) merged.get("max")) / 2);

        return merged;
    }

    private List<String> mergeUniqueLists(List<String> list1, List<String> list2, int limit) {
        Set<String> unique = new LinkedHashSet<>();
        list2.forEach(unique::add); // 새 항목 우선
        list1.forEach(unique::add);
        return unique.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> mergeKeywordsWithTFIDF(List<String> existing, List<String> newKeywords) {
        Map<String, Double> tfidf = new HashMap<>();

        // 단순 TF 계산 (실제로는 전체 문서 대비 IDF 계산 필요)
        Map<String, Integer> termFrequency = new HashMap<>();
        existing.forEach(k -> termFrequency.merge(k, 1, Integer::sum));
        newKeywords.forEach(k -> termFrequency.merge(k, 2, Integer::sum));

        // TF-IDF 근사값 계산
        int totalTerms = termFrequency.values().stream().mapToInt(Integer::intValue).sum();
        termFrequency.forEach((term, freq) -> {
            double tf = (double) freq / totalTerms;
            double idf = Math.log(2.0); // 단순화된 IDF
            tfidf.put(term, tf * idf);
        });

        // 상위 키워드 추출
        return tfidf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String mergeSentiment(String existing, String newSentiment) {
        if (existing == null) return newSentiment;
        if (newSentiment == null) return existing;

        // 감정 점수 매핑
        Map<String, Double> sentimentScores = Map.of(
                "positive", 1.0,
                "neutral", 0.0,
                "negative", -1.0
        );

        double existingScore = sentimentScores.getOrDefault(existing, 0.0);
        double newScore = sentimentScores.getOrDefault(newSentiment, 0.0);

        // 가중 평균 (최근 감정에 더 높은 가중치)
        double mergedScore = existingScore * 0.3 + newScore * 0.7;

        // 점수를 감정으로 변환
        if (mergedScore > 0.3) return "positive";
        else if (mergedScore < -0.3) return "negative";
        else return "neutral";
    }

    private double calculateConfidence(Map<String, Object> preferences) {
        double confidence = 0.0;

        // 각 요소별 신뢰도 계산
        if (preferences.containsKey("categories") && !((List) preferences.get("categories")).isEmpty()) {
            confidence += 0.2;
        }
        if (preferences.containsKey("styles") && !((List) preferences.get("styles")).isEmpty()) {
            confidence += 0.2;
        }
        if (preferences.containsKey("priceRange")) {
            confidence += 0.2;
        }
        if (preferences.containsKey("keywords") && ((List) preferences.get("keywords")).size() > 5) {
            confidence += 0.2;
        }

        // 업데이트 횟수에 따른 보너스
        int updateCount = (Integer) preferences.getOrDefault("updateCount", 0);
        confidence += Math.min(0.2, updateCount * 0.02);

        return Math.min(1.0, confidence);
    }
}