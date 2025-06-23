package com.example.crud.ai.recommendation.infrastructure.event;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.IntegratedRecommendationService;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 추천 관련 이벤트 처리
 * - 메시지 생성 이벤트
 * - 상품 조회 이벤트
 * - 구매 이벤트
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        value = "kafka.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RecommendationEventProcessor {

    private final IntegratedRecommendationService recommendationService; // 변경됨: 통합 서비스 사용
    private final RecommendationCacheService cacheService;
    private final ConversationRepository conversationRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String USER_INTERACTION_KEY = "user:interaction:";
    private static final String PRODUCT_VIEW_KEY = "product:views:";
    private static final String TRENDING_KEY = "recommendation:trending:products";
    private static final String COLLABORATIVE_KEY = "recommendation:collaborative:";

    /**
     * 메시지 생성 이벤트 처리
     * - 사용자 메시지 분석
     * - 비동기 추천 생성
     * - 선호도 업데이트
     */
    @KafkaListener(
            topics = "conversation-messages",
            groupId = "recommendation-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processMessageCreated(String message) {
        try {
            MsgCreatedPayload payload = Json.decode(message, MsgCreatedPayload.class);

            if (!MessageType.USER.name().equals(payload.type().name())) {
                return; // 사용자 메시지만 처리
            }

            log.debug("메시지 이벤트 처리: convId={}, content={}",
                    payload.conversationId(), payload.content());

            // 대화 정보 조회
            Conversation conversation = conversationRepository
                    .findById(payload.conversationId())
                    .orElse(null);

            if (conversation != null && conversation.getMember() != null) {
                Long userId = conversation.getMember().getNumber();

                // 비동기로 추천 생성 (캐시 워밍)
                generateAsyncRecommendations(userId, payload.content());

                // 사용자 상호작용 기록
                recordUserInteraction(userId, payload.content());
            }

        } catch (Exception e) {
            log.error("메시지 이벤트 처리 실패", e);
        }
    }

    /**
     * 상품 조회 이벤트 처리
     * - 조회수 증가
     * - 트렌드 점수 업데이트
     * - 협업 필터링 데이터 수집
     */
    @KafkaListener(
            topics = "product-views",
            groupId = "recommendation-processor"
    )
    public void processProductView(String message) {
        try {
            Map<String, Object> event = Json.decode(message, Map.class);
            Long userId = Long.valueOf(event.get("userId").toString());
            Long productId = Long.valueOf(event.get("productId").toString());

            // 1. 조회수 증가
            incrementProductViews(productId);

            // 2. 트렌드 점수 업데이트
            updateTrendingScore(productId);

            // 3. 협업 필터링 데이터 수집
            if (userId != null) {
                collectCollaborativeData(userId, productId, "view");
            }

            log.debug("상품 조회 처리 완료: userId={}, productId={}", userId, productId);

        } catch (Exception e) {
            log.error("상품 조회 이벤트 처리 실패", e);
        }
    }

    /**
     * 구매 이벤트 처리
     * - 구매 데이터 기반 추천 업데이트
     * - 협업 필터링 강화
     */
    @KafkaListener(
            topics = "order-completed",
            groupId = "recommendation-processor"
    )
    public void processPurchase(String message) {
        try {
            Map<String, Object> event = Json.decode(message, Map.class);
            Long userId = Long.valueOf(event.get("userId").toString());
            List<Long> productIds = (List<Long>) event.get("productIds");

            for (Long productId : productIds) {
                // 1. 구매 기반 협업 필터링 데이터
                collectCollaborativeData(userId, productId, "purchase");

                // 2. 구매 트렌드 반영
                updateTrendingScore(productId, 5.0); // 구매는 더 높은 가중치
            }

            // 3. 구매 후 추천 갱신
            refreshUserRecommendations(userId);

            log.info("구매 이벤트 처리 완료: userId={}, products={}", userId, productIds);

        } catch (Exception e) {
            log.error("구매 이벤트 처리 실패", e);
        }
    }

    /**
     * 비동기 추천 생성 (캐시 워밍)
     * IntegratedRecommendationService 사용
     */
    private void generateAsyncRecommendations(Long userId, String message) {
        try {
            // 통합 추천 서비스 사용
            List<ProductResponseDto> recommendations = recommendationService.recommend(userId, message);

            // 캐시 저장은 서비스 내부에서 처리됨
            log.debug("비동기 추천 생성 완료: userId={}, count={}",
                    userId, recommendations.size());

            // 추천 품질 메트릭 수집
            updateRecommendationMetrics(userId, recommendations);

        } catch (Exception e) {
            log.warn("비동기 추천 생성 실패: userId={}", userId, e);
        }
    }

    /**
     * 사용자 상호작용 기록
     */
    private void recordUserInteraction(Long userId, String content) {
        try {
            String key = USER_INTERACTION_KEY + userId;
            Map<String, Object> interaction = new HashMap<>();
            interaction.put("content", content);
            interaction.put("timestamp", System.currentTimeMillis());

            // 리스트에 추가 (최근 100개만 유지)
            redisTemplate.opsForList().leftPush(key, Json.encode(interaction));
            redisTemplate.opsForList().trim(key, 0, 99);
            redisTemplate.expire(key, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("상호작용 기록 실패: {}", e.getMessage());
        }
    }

    /**
     * 상품 조회수 증가
     */
    private void incrementProductViews(Long productId) {
        try {
            String key = PRODUCT_VIEW_KEY + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForHash().increment(key, productId.toString(), 1);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("조회수 증가 실패: {}", e.getMessage());
        }
    }

    /**
     * 트렌드 점수 업데이트
     */
    private void updateTrendingScore(Long productId) {
        updateTrendingScore(productId, 1.0);
    }

    private void updateTrendingScore(Long productId, double score) {
        try {
            // 시간 가중치 적용 (최근일수록 높은 점수)
            double timeWeight = 1.0 / (1.0 + (System.currentTimeMillis() / (1000 * 60 * 60 * 24)));
            double weightedScore = score * timeWeight;

            redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, productId.toString(), weightedScore);

            // 상위 1000개만 유지
            Long size = redisTemplate.opsForZSet().size(TRENDING_KEY);
            if (size != null && size > 1000) {
                redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, size - 1001);
            }

        } catch (Exception e) {
            log.debug("트렌드 점수 업데이트 실패: {}", e.getMessage());
        }
    }

    /**
     * 협업 필터링 데이터 수집
     */
    private void collectCollaborativeData(Long userId, Long productId, String action) {
        try {
            // 사용자별 상품 점수
            String userKey = COLLABORATIVE_KEY + userId;
            double score = "purchase".equals(action) ? 5.0 : 1.0;

            redisTemplate.opsForHash().increment(userKey, productId.toString(), score);
            redisTemplate.expire(userKey, 90, TimeUnit.DAYS);

            // 상품별 사용자 점수 (역인덱스)
            String productKey = COLLABORATIVE_KEY + "product:" + productId;
            redisTemplate.opsForHash().increment(productKey, userId.toString(), score);
            redisTemplate.expire(productKey, 90, TimeUnit.DAYS);

            // 유사 사용자 찾기 및 추천 업데이트 (비동기)
            updateSimilarUserRecommendations(userId, productId);

        } catch (Exception e) {
            log.debug("협업 필터링 데이터 수집 실패: {}", e.getMessage());
        }
    }

    /**
     * 유사 사용자 기반 추천 업데이트
     */
    private void updateSimilarUserRecommendations(Long userId, Long productId) {
        try {
            String productKey = COLLABORATIVE_KEY + "product:" + productId;
            Map<Object, Object> userScores = redisTemplate.opsForHash().entries(productKey);

            // 같은 상품을 본/구매한 다른 사용자들 찾기
            for (Map.Entry<Object, Object> entry : userScores.entrySet()) {
                Long otherUserId = Long.parseLong(entry.getKey().toString());

                if (!otherUserId.equals(userId)) {
                    // 다른 사용자가 본 상품들을 현재 사용자에게 추천
                    copySimilarUserProducts(userId, otherUserId);
                }
            }

        } catch (Exception e) {
            log.debug("유사 사용자 추천 업데이트 실패: {}", e.getMessage());
        }
    }

    /**
     * 유사 사용자의 상품 복사
     */
    private void copySimilarUserProducts(Long targetUserId, Long sourceUserId) {
        try {
            String sourceKey = COLLABORATIVE_KEY + sourceUserId;
            String targetKey = COLLABORATIVE_KEY + targetUserId;

            Map<Object, Object> sourceProducts = redisTemplate.opsForHash().entries(sourceKey);

            for (Map.Entry<Object, Object> entry : sourceProducts.entrySet()) {
                // 낮은 가중치로 추가 (간접 추천)
                redisTemplate.opsForHash().increment(
                        targetKey,
                        entry.getKey().toString(),
                        Double.parseDouble(entry.getValue().toString()) * 0.1
                );
            }

        } catch (Exception e) {
            log.debug("유사 사용자 상품 복사 실패: {}", e.getMessage());
        }
    }

    /**
     * 사용자 추천 갱신
     */
    private void refreshUserRecommendations(Long userId) {
        try {
            // 캐시 무효화
            cacheService.invalidateUserCache(userId);

            // 새로운 추천 생성 트리거
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("action", "refresh");
            event.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("recommendation-refresh", Json.encode(event));

        } catch (Exception e) {
            log.warn("추천 갱신 실패: userId={}", userId, e);
        }
    }

    /**
     * 추천 메트릭 업데이트
     */
    private void updateRecommendationMetrics(Long userId, List<ProductResponseDto> recommendations) {
        try {
            String metricsKey = "metrics:recommendation:" + LocalDateTime.now().toLocalDate();

            // 일별 추천 생성 횟수
            redisTemplate.opsForHash().increment(metricsKey, "total_generated", 1);

            // 사용자별 추천 횟수
            redisTemplate.opsForHash().increment(metricsKey, "user:" + userId, 1);

            // 평균 추천 개수
            redisTemplate.opsForHash().put(metricsKey, "avg_count:" + userId, recommendations.size());

            // TTL 설정
            redisTemplate.expire(metricsKey, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("메트릭 업데이트 실패: {}", e.getMessage());
        }
    }
}