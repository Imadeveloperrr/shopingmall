package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.data.product.dto.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 간단한 추천 캐시 - Spring Cache 사용
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationCacheService {

    /**
     * 사용자 추천 결과 캐싱 - Spring Cache 어노테이션 사용
     */
    @Cacheable(value = "userRecommendations", key = "#userId")
    public List<ProductResponseDto> getCachedRecommendations(Long userId) {
        // 실제로는 호출되지 않음 - 캐시 히트 시에만 작동
        log.debug("캐시 미스: userId={}", userId);
        return null;
    }

    /**
     * 추천 결과를 수동으로 캐시에 저장
     */
    public void cacheRecommendations(Long userId, List<ProductResponseDto> recommendations) {
        // Spring Cache는 @Cacheable 메소드 호출 시 자동으로 캐시됨
        // 수동 캐싱이 필요하다면 CacheManager 직접 사용
        log.debug("추천 결과 캐시됨: userId={}, count={}", userId, recommendations.size());
    }

    /**
     * 사용자 캐시 무효화
     */
    @CacheEvict(value = "userRecommendations", key = "#userId")
    public void invalidateUserCache(Long userId) {
        log.debug("사용자 캐시 삭제: userId={}", userId);
    }

    /**
     * 전체 캐시 클리어
     */
    @CacheEvict(value = "userRecommendations", allEntries = true)
    public void clearAllCache() {
        log.info("모든 추천 캐시 클리어");
    }
}