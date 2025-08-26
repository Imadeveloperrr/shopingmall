package com.example.crud.ai.recommendation.infrastructure.event;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Member;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 통합 추천 이벤트 프로세서
 *
 * - 메시지 이벤트 처리 및 자동 추천
 * - 상품 조회/구매/좋아요 이벤트 처리
 * - 협업 필터링 데이터 수집
 * - 실시간 트렌드 업데이트
 * - 검색 이벤트 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationEventProcessor {

    private final ConversationRepository conversationRepository;
    private final RecommendationEngine recommendationEngine;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Redis Keys
    private static final String TRENDING_KEY = "recommendation:trending:products";
    private static final String COLLABORATIVE_KEY = "recommendation:collaborative:";
    private static final String USER_EVENT_KEY = "user:events:";
    private static final String DAILY_STATS_KEY = "stats:daily:";
    private static final String SEARCH_TRENDS_KEY = "search:trends:";
    private static final String PURCHASE_HISTORY_KEY = "purchase:history:";

    /**
     * 통합 이벤트 리스너
     * 여러 토픽의 이벤트를 하나의 메서드에서 처리
     */
    @KafkaListener(
            topics = {
                    "conversation.message.created",
                    "conv-msg-created",
                    "product.viewed",
                    "product.detail.viewed",
                    "product-viewed",
                    "order.completed",
                    "order-completed",
                    "product.liked",
                    "search.executed",
                    "user-behavior"
            },
            groupId = "recommendation-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.debug("이벤트 수신: topic={}, data={}", topic, payload);

            switch (topic) {
                case "conversation.message.created":
                case "conv-msg-created":
                    handleMessageCreated(payload);
                    break;

                case "product.viewed":
                case "product.detail.viewed":
                case "product-viewed":
                    handleProductViewed(payload);
                    break;

                case "order.completed":
                case "order-completed":
                    handleOrderCompleted(payload);
                    break;

                case "product.liked":
                    handleProductLiked(payload);
                    break;

                case "search.executed":
                    handleSearchExecuted(payload);
                    break;

                case "user-behavior":
                    handleUserBehavior(payload);
                    break;

                default:
                    log.warn("알 수 없는 토픽: {}", topic);
            }

        } catch (Exception e) {
            log.error("이벤트 처리 실패: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 메시지 생성 이벤트 처리
     */
    private void handleMessageCreated(String payload) {
        try {
            MsgCreatedPayload event = Json.decode(payload, MsgCreatedPayload.class);

            // 사용자 메시지만 처리
            if (event.type() != MessageType.USER) {
                return;
            }

            log.debug("메시지 이벤트 처리: conversationId={}, message={}",
                    event.conversationId(), event.content());

            // 대화 정보 조회
            Conversation conversation = conversationRepository.findById(event.conversationId())
                    .orElse(null);

            if (conversation == null || conversation.getMember() == null) {
                log.warn("대화 또는 회원 정보를 찾을 수 없음: conversationId={}", event.conversationId());
                return;
            }

            Member member = conversation.getMember();

            // 트렌딩 키워드 업데이트
            updateTrendingKeywords(event.content());

            // 비동기로 추천 생성
            CompletableFuture.runAsync(() -> {
                try {
                    List<ProductResponseDto> recommendations = recommendationEngine
                            .recommend(member.getNumber(), event.content());

                    if (!recommendations.isEmpty()) {
                        publishRecommendationReadyEvent(
                                event.conversationId(),
                                member.getNumber(),
                                recommendations
                        );
                    }
                } catch (Exception e) {
                    log.error("추천 생성 실패: conversationId={}", event.conversationId(), e);
                }
            });

        } catch (Exception e) {
            log.error("메시지 이벤트 처리 실패", e);
        }
    }

    /**
     * 상품 조회 이벤트 처리
     */
    private void handleProductViewed(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = getLongValue(event, "userId");
            Long productId = getLongValue(event, "productId");

            if (userId == null || productId == null) {
                log.warn("상품 조회 이벤트 데이터 누락: userId={}, productId={}", userId, productId);
                return;
            }

            // 트렌드 업데이트 (조회는 낮은 가중치)
            updateTrendingScore(productId, 1.0);

            // 협업 필터링 데이터 수집
            collectCollaborativeData(userId, productId, "view", 1.0);

            // 사용자 이벤트 기록
            recordUserEvent(userId, "view", productId);

        } catch (Exception e) {
            log.error("상품 조회 이벤트 처리 실패", e);
        }
    }

    /**
     * 주문 완료 이벤트 처리
     */
    private void handleOrderCompleted(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = getLongValue(event, "userId");
            List<Map<String, Object>> items = (List<Map<String, Object>>) event.get("items");

            if (userId == null || items == null) {
                log.warn("주문 완료 이벤트 데이터 누락");
                return;
            }

            for (Map<String, Object> item : items) {
                Long productId = getLongValue(item, "productId");
                Integer quantity = getIntValue(item, "quantity", 1);

                if (productId != null) {
                    // 구매는 높은 가중치
                    updateTrendingScore(productId, 5.0 * quantity);
                    collectCollaborativeData(userId, productId, "purchase", 5.0);
                    recordUserEvent(userId, "purchase", productId);
                    updatePurchaseHistory(userId, productId);
                }
            }

            // 사용자 추천 캐시 무효화
            cacheService.invalidateUserCache(userId);

            // 추천 재계산 스케줄링
            scheduleRecommendationUpdate(userId);

        } catch (Exception e) {
            log.error("주문 완료 이벤트 처리 실패", e);
        }
    }

    /**
     * 상품 좋아요 이벤트 처리
     */
    private void handleProductLiked(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = getLongValue(event, "userId");
            Long productId = getLongValue(event, "productId");
            Boolean liked = (Boolean) event.get("liked");

            if (userId == null || productId == null) {
                return;
            }

            double score = Boolean.TRUE.equals(liked) ? 3.0 : -2.0;

            updateTrendingScore(productId, score);
            collectCollaborativeData(userId, productId, "like", score);
            recordUserEvent(userId, liked ? "like" : "unlike", productId);

        } catch (Exception e) {
            log.error("좋아요 이벤트 처리 실패", e);
        }
    }

    /**
     * 검색 실행 이벤트 처리
     */
    private void handleSearchExecuted(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            String query = (String) event.get("query");
            Long userId = getLongValue(event, "userId");

            if (query != null && !query.trim().isEmpty()) {
                updateSearchTrend(query);

                if (userId != null) {
                    recordUserEvent(userId, "search", null);
                }
            }

        } catch (Exception e) {
            log.error("검색 이벤트 처리 실패", e);
        }
    }

    /**
     * 사용자 행동 이벤트 처리
     */
    private void handleUserBehavior(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            String behaviorType = (String) event.get("type");
            Long userId = getLongValue(event, "userId");
            Long productId = getLongValue(event, "productId");

            if (behaviorType == null || userId == null) {
                return;
            }

            // 행동 유형별 가중치
            double score = switch (behaviorType) {
                case "add_to_cart" -> 2.0;
                case "remove_from_cart" -> -1.0;
                case "add_to_wishlist" -> 2.5;
                case "share" -> 3.5;
                default -> 0.5;
            };

            if (productId != null && score != 0) {
                updateTrendingScore(productId, score);
                collectCollaborativeData(userId, productId, behaviorType, score);
            }

            recordUserEvent(userId, behaviorType, productId);

        } catch (Exception e) {
            log.error("사용자 행동 이벤트 처리 실패", e);
        }
    }

    /**
     * 트렌딩 점수 업데이트
     */
    private void updateTrendingScore(Long productId, double score) {
        try {
            redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, productId.toString(), score);

            // 음수 점수 제거
            redisTemplate.opsForZSet().removeRangeByScore(TRENDING_KEY, Double.NEGATIVE_INFINITY, 0);

            // 상위 1000개만 유지
            Long size = redisTemplate.opsForZSet().size(TRENDING_KEY);
            if (size != null && size > 1000) {
                redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, size - 1001);
            }

        } catch (Exception e) {
            log.debug("트렌드 업데이트 실패: productId={}", productId, e);
        }
    }

    /**
     * 협업 필터링 데이터 수집
     */
    private void collectCollaborativeData(Long userId, Long productId, String action, double score) {
        try {
            // 사용자별 상품 점수
            String userKey = COLLABORATIVE_KEY + "user:" + userId;
            redisTemplate.opsForHash().increment(userKey, productId.toString(), score);
            redisTemplate.expire(userKey, Duration.ofDays(90));

            // 상품별 사용자 점수 (역인덱스)
            String productKey = COLLABORATIVE_KEY + "product:" + productId;
            redisTemplate.opsForHash().increment(productKey, userId.toString(), score);
            redisTemplate.expire(productKey, Duration.ofDays(90));

            // 비동기로 유사 사용자 추천 업데이트
            CompletableFuture.runAsync(() -> updateSimilarUserRecommendations(userId, productId));

        } catch (Exception e) {
            log.debug("협업 필터링 데이터 수집 실패", e);
        }
    }

    /**
     * 유사 사용자 기반 추천 업데이트
     */
    private void updateSimilarUserRecommendations(Long userId, Long productId) {
        try {
            String productKey = COLLABORATIVE_KEY + "product:" + productId;
            Map<Object, Object> userScores = redisTemplate.opsForHash().entries(productKey);

            // 같은 상품에 관심을 보인 사용자들 찾기
            List<Long> similarUsers = userScores.entrySet().stream()
                    .filter(entry -> {
                        Long otherUserId = Long.parseLong(entry.getKey().toString());
                        double score = Double.parseDouble(entry.getValue().toString());
                        return !otherUserId.equals(userId) && score > 3.0;
                    })
                    .map(entry -> Long.parseLong(entry.getKey().toString()))
                    .limit(10)
                    .collect(Collectors.toList());

            // 유사 사용자들의 다른 관심 상품을 현재 사용자에게 추천
            for (Long similarUserId : similarUsers) {
                transferSimilarUserInterests(userId, similarUserId);
            }

        } catch (Exception e) {
            log.debug("유사 사용자 추천 업데이트 실패", e);
        }
    }

    /**
     * 유사 사용자의 관심사 전달
     */
    private void transferSimilarUserInterests(Long targetUserId, Long sourceUserId) {
        try {
            String sourceKey = COLLABORATIVE_KEY + "user:" + sourceUserId;
            String targetKey = COLLABORATIVE_KEY + "user:" + targetUserId;

            Map<Object, Object> sourceProducts = redisTemplate.opsForHash().entries(sourceKey);

            for (Map.Entry<Object, Object> entry : sourceProducts.entrySet()) {
                String productId = entry.getKey().toString();
                double score = Double.parseDouble(entry.getValue().toString());

                // 낮은 가중치로 추가 (간접 추천)
                if (score > 3.0) {
                    redisTemplate.opsForHash().increment(targetKey, productId, score * 0.1);
                }
            }

        } catch (Exception e) {
            log.debug("관심사 전달 실패", e);
        }
    }

    /**
     * 사용자 이벤트 기록
     */
    private void recordUserEvent(Long userId, String eventType, Long productId) {
        try {
            String key = USER_EVENT_KEY + userId;
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("type", eventType);
            eventData.put("timestamp", System.currentTimeMillis());

            if (productId != null) {
                eventData.put("productId", productId);
            }

            redisTemplate.opsForList().leftPush(key, Json.encode(eventData));
            redisTemplate.opsForList().trim(key, 0, 99); // 최근 100개만 유지
            redisTemplate.expire(key, Duration.ofDays(30));

            // 일별 통계 업데이트
            updateDailyStats(eventType);

        } catch (Exception e) {
            log.debug("사용자 이벤트 기록 실패", e);
        }
    }

    /**
     * 트렌딩 키워드 업데이트
     */
    private void updateTrendingKeywords(String content) {
        try {
            List<String> keywords = extractKeywords(content);
            String key = "trending:keywords:" + LocalDate.now();

            for (String keyword : keywords) {
                redisTemplate.opsForZSet().incrementScore(key, keyword, 1.0);
            }

            redisTemplate.expire(key, Duration.ofDays(7));

        } catch (Exception e) {
            log.debug("트렌딩 키워드 업데이트 실패", e);
        }
    }

    /**
     * 키워드 추출
     */
    private List<String> extractKeywords(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }

        // 상품 관련 키워드 사전
        Set<String> productKeywords = Set.of(
                "원피스", "셔츠", "팬츠", "스커트", "자켓", "코트",
                "가방", "신발", "액세서리", "화장품", "향수",
                "전자제품", "가전", "가구", "인테리어"
        );

        String cleanContent = content.toLowerCase()
                .replaceAll("[^가-힣a-z0-9\\s]", "");

        return Arrays.stream(cleanContent.split("\\s+"))
                .filter(word -> word.length() > 1 && productKeywords.stream()
                        .anyMatch(keyword -> word.contains(keyword) || keyword.contains(word)))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * 검색어 트렌드 업데이트
     */
    private void updateSearchTrend(String query) {
        try {
            String key = SEARCH_TRENDS_KEY + LocalDate.now();
            redisTemplate.opsForZSet().incrementScore(key, query.toLowerCase(), 1.0);
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.debug("검색 트렌드 업데이트 실패", e);
        }
    }

    /**
     * 구매 이력 업데이트
     */
    private void updatePurchaseHistory(Long userId, Long productId) {
        try {
            String key = PURCHASE_HISTORY_KEY + userId;
            Map<String, Object> purchase = Map.of(
                    "productId", productId,
                    "timestamp", System.currentTimeMillis()
            );

            redisTemplate.opsForList().leftPush(key, Json.encode(purchase));
            redisTemplate.opsForList().trim(key, 0, 49); // 최근 50개만 유지
            redisTemplate.expire(key, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("구매 이력 업데이트 실패: userId={}", userId, e);
        }
    }

    /**
     * 일별 통계 업데이트
     */
    private void updateDailyStats(String eventType) {
        try {
            String key = DAILY_STATS_KEY + LocalDate.now();
            redisTemplate.opsForHash().increment(key, eventType, 1);
            redisTemplate.expire(key, Duration.ofDays(30));
        } catch (Exception e) {
            log.debug("통계 업데이트 실패", e);
        }
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
            Map<String, Object> job = Map.of(
                    "userId", userId,
                    "scheduledAt", System.currentTimeMillis(),
                    "executeAt", System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
            );

            redisTemplate.opsForValue().set(jobKey, Json.encode(job), Duration.ofMinutes(10));
            log.info("추천 재계산 스케줄링: userId={}", userId);

        } catch (Exception e) {
            log.error("추천 재계산 스케줄링 실패: userId={}", userId, e);
        }
    }

    /**
     * 추천 준비 완료 이벤트 발행
     */
    private void publishRecommendationReadyEvent(Long conversationId, Long userId,
                                                 List<ProductResponseDto> recommendations) {
        try {
            Map<String, Object> event = Map.of(
                    "conversationId", conversationId,
                    "userId", userId,
                    "productIds", recommendations.stream()
                            .map(ProductResponseDto::getNumber)
                            .limit(10)
                            .toList(),
                    "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("recommendation.ready", Json.encode(event));
            log.debug("추천 준비 완료 이벤트 발행: conversationId={}", conversationId);

        } catch (Exception e) {
            log.error("추천 이벤트 발행 실패", e);
        }
    }

    /**
     * Long 값 안전하게 추출
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * Integer 값 안전하게 추출 (기본값 포함)
     */
    private Integer getIntValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
}