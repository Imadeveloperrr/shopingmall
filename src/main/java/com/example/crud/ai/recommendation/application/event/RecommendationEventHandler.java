package com.example.crud.ai.recommendation.application.event;

import com.example.crud.ai.conversation.domain.event.MessageCreatedEvent;
import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.data.product.dto.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 추천 시스템 이벤트 처리 - 간소화된 버전
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationEventHandler {

    private final RecommendationEngine recommendationEngine;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 메시지 생성 시 추천 업데이트
     */
    @EventListener
    @Async
    public void handleMessageCreatedForRecommendation(MessageCreatedEvent event) {
        try {
            if (!"USER".equals(event.getMessageType())) {
                return;
            }

            log.debug("추천 시스템 처리: userId={}, content={}", event.getUserId(), event.getContent());

            // 사용자 캐시 무효화
            cacheService.invalidateUserCache(event.getUserId());

            // 새로운 추천 생성
            List<ProductResponseDto> recommendations = recommendationEngine
                    .recommend(event.getUserId(), event.getContent());

            if (!recommendations.isEmpty()) {
                // 추천 결과 캐싱
                cacheService.cacheRecommendations(event.getUserId(), recommendations);
                
                log.debug("추천 업데이트 완료: userId={}, count={}", 
                    event.getUserId(), recommendations.size());
            }

        } catch (Exception e) {
            log.error("추천 시스템 처리 실패: userId={}", event.getUserId(), e);
        }
    }

    /**
     * 간단한 트렌드 업데이트
     */
    private void updateTrendingScore(Long productId, double score) {
        try {
            String key = "rec:trending";
            redisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);

            // 상위 100개만 유지
            Long size = redisTemplate.opsForZSet().size(key);
            if (size != null && size > 100) {
                redisTemplate.opsForZSet().removeRange(key, 0, size - 101);
            }

        } catch (Exception e) {
            log.debug("트렌드 업데이트 실패: productId={}", productId, e);
        }
    }
}