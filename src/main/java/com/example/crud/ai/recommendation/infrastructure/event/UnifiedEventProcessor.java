package com.example.crud.ai.recommendation.infrastructure.event;

import com.example.crud.ai.recommendation.application.IntegratedRecommendationService;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 통합 이벤트 프로세서
 *
 * 기존의 여러 이벤트 프로세서를 하나로 통합:
 * - RecommendationEventProcessor
 * - PreferenceAnalysisConsumer (필요한 부분만)
 * - MsgCreatedConsumer (ES 인덱싱은 별도 유지)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnifiedEventProcessor {

    private final IntegratedRecommendationService recommendationService;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 다중 토픽 이벤트 처리
     */
    @KafkaListener(
            topics = {"conv-msg-created", "product-viewed", "order-completed", "user-behavior"},
            groupId = "recommendation-processor"
    )
    public void processEvent(
            String json,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {

        try {
            log.debug("이벤트 수신: topic={}, data={}", topic, json);
            Map<String, Object> event = Json.decode(json, Map.class);

            switch (topic) {
                case "conv-msg-created":
                    processMessageEvent(event);
                    break;

                case "product-viewed":
                    processProductViewEvent(event);
                    break;

                case "order-completed":
                    processOrderEvent(event);
                    break;

                case "user-behavior":
                    processUserBehaviorEvent(event);
                    break;

                default:
                    log.warn("알 수 없는 토픽: {}", topic);
            }

        } catch (Exception e) {
            log.error("이벤트 처리 실패: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 대화 메시지 이벤트 처리
     */
    private void processMessageEvent(Map<String, Object> event) {
        String content = (String) event.get("content");
        if (content == null || content.isEmpty()) {
            return;
        }

        // 트렌딩 키워드 업데이트
        updateTrendingKeywords(content);
    }

    /**
     * 상품 조회 이벤트 처리
     */
    private void processProductViewEvent(Map<String, Object> event) {
        Long productId = getLongValue(event, "productId");
        Long userId = getLongValue(event, "userId");

        if (productId == null || userId == null) {
            log.warn("상품 조회 이벤트 데이터 누락: productId={}, userId={}", productId, userId);
            return;
        }

        // 1. 트렌딩 점수 업데이트 (조회는 낮은 점수)
        cacheService.updateTrendingProducts(productId, 1.0);

        // 2. 사용자 행동 기록
        recordUserBehavior(userId, "view", productId);

        // 3. 비동기로 유사 상품 캐싱
        CompletableFuture.runAsync(() -> {
            try {
                generateAndCacheSimilarProducts(productId);
            } catch (Exception e) {
                log.error("유사 상품 캐싱 실패: productId={}", productId, e);
            }
        });
    }

    /**
     * 구매 완료 이벤트 처리
     */
    private void processOrderEvent(Map<String, Object> event) {
        Long userId = getLongValue(event, "userId");
        List<Long> productIds = getProductIds(event);

        if (userId == null || productIds.isEmpty()) {
            log.warn("구매 이벤트 데이터 누락: userId={}, productIds={}", userId, productIds);
            return;
        }

        // 1. 구매 상품 트렌딩 점수 대폭 증가
        productIds.forEach(pid ->
                cacheService.updateTrendingProducts(pid, 10.0)
        );

        // 2. 구매 이력 업데이트
        updatePurchaseHistory(userId, productIds);

        // 3. 사용자별 행동 기록
        productIds.forEach(pid ->
                recordUserBehavior(userId, "purchase", pid)
        );

        // 4. 추천 재계산 스케줄링 (5분 후)
        scheduleRecommendationUpdate(userId);
    }

    /**
     * 사용자 행동 이벤트 처리
     */
    private void processUserBehaviorEvent(Map<String, Object> event) {
        Long userId = getLongValue(event, "userId");
        String action = (String) event.get("action");
        Long productId = getLongValue(event, "productId");

        if (userId == null || action == null || productId == null) {
            return;
        }

        // 행동 유형별 가중치
        double weight = switch (action) {
            case "like" -> 3.0;
            case "cart_add" -> 5.0;
            case "share" -> 2.0;
            case "view_detail" -> 1.5;
            default -> 1.0;
        };

        // 트렌딩 점수 업데이트
        cacheService.updateTrendingProducts(productId, weight);
    }

    /**
     * 트렌딩 키워드 업데이트
     */
    private void updateTrendingKeywords(String content) {
        try {
            // 키워드 추출
            List<String> keywords = extractKeywords(content);
            if (keywords.isEmpty()) {
                return;
            }

            // Redis에 트렌딩 키워드 업데이트
            String trendingKey = "trending:keywords:" + LocalDate.now();

            for (String keyword : keywords) {
                redisTemplate.opsForZSet().incrementScore(trendingKey, keyword, 1.0);
            }

            // 24시간 후 만료
            redisTemplate.expire(trendingKey, Duration.ofDays(1));

            // 상위 100개만 유지
            Long size = redisTemplate.opsForZSet().size(trendingKey);
            if (size != null && size > 100) {
                redisTemplate.opsForZSet().removeRange(trendingKey, 0, size - 101);
            }

        } catch (Exception e) {
            log.error("트렌딩 키워드 업데이트 실패", e);
        }
    }

    /**
     * 사용자 행동 기록
     */
    private void recordUserBehavior(Long userId, String action, Long productId) {
        try {
            // 시계열 데이터로 저장
            String key = String.format("user:behavior:%d:%s", userId, LocalDate.now());

            Map<String, Object> behavior = new HashMap<>();
            behavior.put("action", action);
            behavior.put("productId", productId);
            behavior.put("timestamp", System.currentTimeMillis());

            redisTemplate.opsForList().leftPush(key, Json.encode(behavior));

            // 30일 후 만료
            redisTemplate.expire(key, Duration.ofDays(30));

            // 최근 1000개만 유지
            redisTemplate.opsForList().trim(key, 0, 999);

        } catch (Exception e) {
            log.error("사용자 행동 기록 실패: userId={}, action={}", userId, action, e);
        }
    }

    /**
     * 구매 이력 업데이트
     */
    private void updatePurchaseHistory(Long userId, List<Long> productIds) {
        try {
            String key = "user:purchased:" + userId;

            // 최신 구매가 앞에 오도록 leftPush
            productIds.forEach(pid ->
                    redisTemplate.opsForList().leftPush(key, pid.toString())
            );

            // 1년간 보관
            redisTemplate.expire(key, Duration.ofDays(365));

            // 최근 100개만 유지
            redisTemplate.opsForList().trim(key, 0, 99);

        } catch (Exception e) {
            log.error("구매 이력 업데이트 실패: userId={}", userId, e);
        }
    }

    /**
     * 유사 상품 생성 및 캐싱
     */
    private void generateAndCacheSimilarProducts(Long productId) {
        // 실제 구현은 IntegratedRecommendationService에서 처리
        // 여기서는 캐싱 트리거만
        log.debug("유사 상품 캐싱 요청: productId={}", productId);
    }

    /**
     * 추천 재계산 스케줄링
     */
    private void scheduleRecommendationUpdate(Long userId) {
        try {
            String jobKey = "rec:update:scheduled:" + userId;

            // 이미 스케줄된 작업이 있는지 확인
            if (Boolean.TRUE.equals(redisTemplate.hasKey(jobKey))) {
                return;
            }

            // 5분 후 실행 예약
            Map<String, Object> job = new HashMap<>();
            job.put("userId", userId);
            job.put("scheduledAt", System.currentTimeMillis());
            job.put("executeAt", System.currentTimeMillis() + 300000); // 5분 후

            redisTemplate.opsForValue().set(jobKey, job, Duration.ofMinutes(10));

            log.info("추천 재계산 스케줄링: userId={}", userId);

        } catch (Exception e) {
            log.error("추천 재계산 스케줄링 실패: userId={}", userId, e);
        }
    }

    /**
     * 키워드 추출
     */
    private List<String> extractKeywords(String content) {
        List<String> keywords = new ArrayList<>();

        try {
            String cleanContent = content.toLowerCase()
                    .replaceAll("[^가-힣a-z0-9\\s]", "");
            String[] words = cleanContent.split("\\s+");

            // 상품 관련 키워드 사전
            Set<String> productKeywords = Set.of(
                    // 카테고리
                    "상의", "하의", "아우터", "원피스", "가방", "신발", "악세서리",
                    "셔츠", "티셔츠", "니트", "자켓", "코트", "패딩",
                    // 스타일
                    "캐주얼", "포멀", "스포티", "빈티지", "모던",
                    // 속성
                    "편한", "따뜻한", "시원한", "예쁜", "귀여운",
                    // 색상
                    "검정", "흰색", "회색", "네이비", "베이지"
            );

            for (String word : words) {
                if (word.length() > 1 && productKeywords.contains(word)) {
                    keywords.add(word);
                }
            }

        } catch (Exception e) {
            log.error("키워드 추출 실패", e);
        }

        return keywords.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 헬퍼 메서드: Long 값 추출
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 헬퍼 메서드: 상품 ID 목록 추출
     */
    private List<Long> getProductIds(Map<String, Object> event) {
        Object productIds = event.get("productIds");
        if (productIds instanceof List) {
            return ((List<?>) productIds).stream()
                    .filter(id -> id instanceof Number)
                    .map(id -> ((Number) id).longValue())
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}