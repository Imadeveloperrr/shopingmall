package com.example.crud.ai.recommendation.infrastructure.event;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.IntegratedRecommendationService;
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

/**
 * 통합 추천 이벤트 프로세서
 * - 메시지 이벤트 처리
 * - 상품 조회/구매 이벤트 처리
 * - 협업 필터링 데이터 수집
 * - 실시간 트렌드 업데이트
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationEventProcessor {

    private final ConversationRepository conversationRepository;
    private final IntegratedRecommendationService recommendationService;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Redis Keys
    private static final String TRENDING_KEY = "recommendation:trending:products";
    private static final String COLLABORATIVE_KEY = "recommendation:collaborative:";
    private static final String USER_EVENT_KEY = "user:events:";
    private static final String DAILY_STATS_KEY = "stats:daily:";

    /**
     * 메시지 생성 이벤트 처리
     * - 사용자 메시지에 대한 자동 추천
     */
    @KafkaListener(
            topics = "conversation.message.created",
            groupId = "recommendation-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleMessageCreated(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
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

            if (conversation == null) {
                log.warn("대화를 찾을 수 없음: conversationId={}", event.conversationId());
                return;
            }

            Member member = conversation.getMember();
            if (member == null) {
                return;
            }

            // 비동기로 추천 생성
            CompletableFuture.runAsync(() -> {
                try {
                    List<ProductResponseDto> recommendations = recommendationService
                            .recommend(member.getNumber(), event.content());

                    if (!recommendations.isEmpty()) {
                        // 추천 결과 이벤트 발행
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
            log.error("메시지 이벤트 처리 실패: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    /**
     * 상품 조회 이벤트 처리
     * - 트렌드 업데이트
     * - 협업 필터링 데이터 수집
     */
    @KafkaListener(
            topics = {"product.viewed", "product.detail.viewed"},
            groupId = "recommendation-processor"
    )
    public void handleProductViewed(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();
            Long productId = ((Number) event.get("productId")).longValue();

            // 트렌드 업데이트
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
     * 상품 구매 이벤트 처리
     * - 높은 가중치로 협업 필터링 데이터 업데이트
     */
    @KafkaListener(
            topics = "order.completed",
            groupId = "recommendation-processor"
    )
    public void handleOrderCompleted(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();
            List<Map<String, Object>> items = (List<Map<String, Object>>) event.get("items");

            for (Map<String, Object> item : items) {
                Long productId = ((Number) item.get("productId")).longValue();
                Integer quantity = ((Number) item.getOrDefault("quantity", 1)).intValue();

                // 구매는 높은 가중치
                updateTrendingScore(productId, 5.0 * quantity);
                collectCollaborativeData(userId, productId, "purchase", 5.0);
                recordUserEvent(userId, "purchase", productId);
            }

            // 사용자 추천 캐시 무효화 (구매 후 새로운 추천 필요)
            cacheService.invalidateUserCache(userId);

        } catch (Exception e) {
            log.error("주문 완료 이벤트 처리 실패", e);
        }
    }

    /**
     * 상품 좋아요 이벤트 처리
     */
    @KafkaListener(
            topics = "product.liked",
            groupId = "recommendation-processor"
    )
    public void handleProductLiked(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();
            Long productId = ((Number) event.get("productId")).longValue();
            Boolean liked = (Boolean) event.get("liked");

            double score = liked ? 3.0 : -3.0;
            updateTrendingScore(productId, score);
            collectCollaborativeData(userId, productId, "like", score);

        } catch (Exception e) {
            log.error("좋아요 이벤트 처리 실패", e);
        }
    }

    /**
     * 검색 이벤트 처리
     * - 검색어 기반 트렌드 분석
     */
    @KafkaListener(
            topics = "search.performed",
            groupId = "recommendation-processor"
    )
    public void handleSearchPerformed(String payload) {
        try {
            Map<String, Object> event = Json.decode(payload, Map.class);
            String query = (String) event.get("query");
            List<Long> resultIds = (List<Long>) event.get("resultIds");

            // 검색 결과 상품들의 트렌드 점수 약간 증가
            if (resultIds != null) {
                for (Long productId : resultIds) {
                    updateTrendingScore(productId, 0.1);
                }
            }

            // 검색어 트렌드 업데이트
            updateSearchTrend(query);

        } catch (Exception e) {
            log.error("검색 이벤트 처리 실패", e);
        }
    }

    /**
     * 트렌드 점수 업데이트
     */
    private void updateTrendingScore(Long productId, double score) {
        try {
            // 시간 가중치 적용 (최근일수록 높은 점수)
            double timeWeight = 1.0;
            double weightedScore = score * timeWeight;

            redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, productId.toString(), weightedScore);

            // TTL 설정 (30일)
            redisTemplate.expire(TRENDING_KEY, Duration.ofDays(30));

            // 일별 트렌드도 업데이트
            String dailyKey = TRENDING_KEY + ":" + LocalDate.now();
            redisTemplate.opsForZSet().incrementScore(dailyKey, productId.toString(), score);
            redisTemplate.expire(dailyKey, Duration.ofDays(7));

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
            List<Long> similarUsers = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : userScores.entrySet()) {
                Long otherUserId = Long.parseLong(entry.getKey().toString());
                if (!otherUserId.equals(userId) && Double.parseDouble(entry.getValue().toString()) > 3.0) {
                    similarUsers.add(otherUserId);
                }
            }

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
            Map<String, Object> eventData = Map.of(
                    "type", eventType,
                    "productId", productId,
                    "timestamp", System.currentTimeMillis()
            );

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
     * 검색어 트렌드 업데이트
     */
    private void updateSearchTrend(String query) {
        try {
            String key = "search:trends:" + LocalDate.now();
            redisTemplate.opsForZSet().incrementScore(key, query.toLowerCase(), 1.0);
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.debug("검색 트렌드 업데이트 실패", e);
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
}