package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 추천 결과 캐싱 서비스
 * - 사용자별 맞춤 추천 캐싱
 * - 카테고리별 추천 캐싱
 * - 인기 상품 캐싱
 * - 실시간 추천 업데이트
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // 캐시 키 프리픽스
    private static final String USER_RECOMMENDATION_KEY = "rec:user:";
    private static final String CATEGORY_POPULAR_KEY = "rec:popular:category:";
    private static final String TRENDING_KEY = "rec:trending";
    private static final String SIMILAR_PRODUCT_KEY = "rec:similar:";
    private static final String RECENT_RECOMMENDATION_KEY = "rec:recent:";
    private static final String CACHE_STATS_KEY = "rec:stats:";

    // 캐시 TTL
    private static final Duration USER_CACHE_TTL = Duration.ofHours(6);
    private static final Duration CATEGORY_CACHE_TTL = Duration.ofHours(1);
    private static final Duration TRENDING_CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration SIMILAR_CACHE_TTL = Duration.ofHours(12);

    /**
     * 사용자 맞춤 추천 캐싱
     */
    public void cacheUserRecommendations(Long userId, List<ProductResponseDto> products) {
        try {
            String key = USER_RECOMMENDATION_KEY + userId;
            String json = objectMapper.writeValueAsString(products);

            redisTemplate.opsForValue().set(key, json, USER_CACHE_TTL);

            // 추천 히스토리 추가 (최근 10개만 유지)
            String historyKey = key + ":history";
            redisTemplate.opsForList().leftPush(historyKey, json);
            redisTemplate.opsForList().trim(historyKey, 0, 9);
            redisTemplate.expire(historyKey, Duration.ofDays(7));

            // 캐시 통계 업데이트
            updateCacheStats("user_cache", "save");

            log.debug("사용자 추천 캐시 저장: userId={}, size={}", userId, products.size());

        } catch (Exception e) {
            log.error("추천 캐싱 실패: userId={}", userId, e);
        }
    }

    /**
     * 캐시된 사용자 추천 조회
     */
    public Optional<List<ProductResponseDto>> getCachedUserRecommendations(Long userId) {
        try {
            String key = USER_RECOMMENDATION_KEY + userId;
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                List<ProductResponseDto> products = objectMapper.readValue(
                        cached.toString(),
                        new TypeReference<List<ProductResponseDto>>() {}
                );

                // 캐시 통계 업데이트
                updateCacheStats("user_cache", "hit");

                log.debug("캐시 히트: userId={}, size={}", userId, products.size());
                return Optional.of(products);
            }

            // 캐시 미스
            updateCacheStats("user_cache", "miss");

        } catch (Exception e) {
            log.error("캐시 조회 실패: userId={}", userId, e);
        }

        return Optional.empty();
    }

    /**
     * 사용자 캐시 무효화
     */
    public void invalidateUserCache(Long userId) {
        try {
            String key = USER_RECOMMENDATION_KEY + userId;
            Boolean deleted = redisTemplate.delete(key);

            if (Boolean.TRUE.equals(deleted)) {
                log.debug("사용자 캐시 삭제: userId={}", userId);
            }

        } catch (Exception e) {
            log.error("캐시 무효화 실패: userId={}", userId, e);
        }
    }

    /**
     * 카테고리별 인기 상품 캐싱
     */
    public void cacheCategoryPopular(String category, List<ProductResponseDto> products) {
        try {
            String key = CATEGORY_POPULAR_KEY + category;
            String json = objectMapper.writeValueAsString(products);

            redisTemplate.opsForValue().set(key, json, CATEGORY_CACHE_TTL);

            updateCacheStats("category_cache", "save");

        } catch (Exception e) {
            log.error("카테고리 캐싱 실패: category={}", category, e);
        }
    }

    /**
     * 카테고리 인기 상품 조회
     */
    public Optional<List<ProductResponseDto>> getCategoryPopular(String category) {
        try {
            String key = CATEGORY_POPULAR_KEY + category;
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                List<ProductResponseDto> products = objectMapper.readValue(
                        cached.toString(),
                        new TypeReference<List<ProductResponseDto>>() {}
                );

                updateCacheStats("category_cache", "hit");
                return Optional.of(products);
            }

            updateCacheStats("category_cache", "miss");

        } catch (Exception e) {
            log.error("카테고리 캐시 조회 실패: category={}", category, e);
        }

        return Optional.empty();
    }

    /**
     * 실시간 트렌드 상품 업데이트
     */
    public void updateTrendingProducts(Map<Long, Double> productScores) {
        try {
            productScores.forEach((productId, score) -> {
                redisTemplate.opsForZSet().add(TRENDING_KEY, productId.toString(), score);
            });

            // 상위 100개만 유지
            Long size = redisTemplate.opsForZSet().size(TRENDING_KEY);
            if (size != null && size > 100) {
                redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, size - 101);
            }

            redisTemplate.expire(TRENDING_KEY, TRENDING_CACHE_TTL);

        } catch (Exception e) {
            log.error("트렌드 업데이트 실패", e);
        }
    }

    /**
     * 트렌드 상품 조회
     */
    public List<Long> getTrendingProductIds(int limit) {
        try {
            Set<Object> trending = redisTemplate.opsForZSet()
                    .reverseRange(TRENDING_KEY, 0, limit - 1);

            if (trending != null) {
                return trending.stream()
                        .map(obj -> Long.parseLong(obj.toString()))
                        .toList();
            }

        } catch (Exception e) {
            log.error("트렌드 조회 실패", e);
        }

        return new ArrayList<>();
    }

    /**
     * 유사 상품 캐싱
     */
    public void cacheSimilarProducts(Long productId, List<ProductResponseDto> similar) {
        try {
            String key = SIMILAR_PRODUCT_KEY + productId;
            String json = objectMapper.writeValueAsString(similar);

            redisTemplate.opsForValue().set(key, json, SIMILAR_CACHE_TTL);

        } catch (Exception e) {
            log.error("유사 상품 캐싱 실패: productId={}", productId, e);
        }
    }

    /**
     * 유사 상품 조회
     */
    public Optional<List<ProductResponseDto>> getSimilarProducts(Long productId) {
        try {
            String key = SIMILAR_PRODUCT_KEY + productId;
            Object cached = redisTemplate.opsForValue().get(key);

            if (cached != null) {
                return Optional.of(objectMapper.readValue(
                        cached.toString(),
                        new TypeReference<List<ProductResponseDto>>() {}
                ));
            }

        } catch (Exception e) {
            log.error("유사 상품 조회 실패: productId={}", productId, e);
        }

        return Optional.empty();
    }

    /**
     * 최근 추천 기록
     */
    public void recordRecentRecommendation(Long userId, String query, int resultCount) {
        try {
            String key = RECENT_RECOMMENDATION_KEY + userId;

            Map<String, Object> record = new HashMap<>();
            record.put("query", query);
            record.put("resultCount", resultCount);
            record.put("timestamp", System.currentTimeMillis());

            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(record));
            redisTemplate.opsForList().trim(key, 0, 19); // 최근 20개만
            redisTemplate.expire(key, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("최근 추천 기록 실패: {}", e.getMessage());
        }
    }

    /**
     * 추천 히스토리 조회
     */
    public List<Map<String, Object>> getRecommendationHistory(Long userId) {
        try {
            String historyKey = USER_RECOMMENDATION_KEY + userId + ":history";
            List<Object> history = redisTemplate.opsForList().range(historyKey, 0, 9);

            if (history != null) {
                return history.stream()
                        .map(item -> {
                            try {
                                List<ProductResponseDto> products = objectMapper.readValue(
                                        item.toString(),
                                        new TypeReference<List<ProductResponseDto>>() {}
                                );

                                Map<String, Object> historyItem = new HashMap<>();
                                historyItem.put("products", products);
                                historyItem.put("count", products.size());
                                return historyItem;

                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .toList();
            }

        } catch (Exception e) {
            log.error("히스토리 조회 실패: userId={}", userId, e);
        }

        return new ArrayList<>();
    }

    /**
     * 캐시 통계 업데이트
     */
    private void updateCacheStats(String cacheType, String operation) {
        try {
            String key = CACHE_STATS_KEY + cacheType;
            redisTemplate.opsForHash().increment(key, operation, 1);
            redisTemplate.expire(key, 7, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("캐시 통계 업데이트 실패: {}", e.getMessage());
        }
    }

    /**
     * 캐시 통계 조회
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 각 캐시 타입별 통계
            List<String> cacheTypes = List.of("user_cache", "category_cache");

            for (String cacheType : cacheTypes) {
                String key = CACHE_STATS_KEY + cacheType;
                Map<Object, Object> typeStats = redisTemplate.opsForHash().entries(key);

                if (!typeStats.isEmpty()) {
                    Map<String, Long> converted = new HashMap<>();
                    typeStats.forEach((k, v) ->
                            converted.put(k.toString(), Long.parseLong(v.toString())));

                    // 히트율 계산
                    long hits = converted.getOrDefault("hit", 0L);
                    long misses = converted.getOrDefault("miss", 0L);
                    long total = hits + misses;

                    if (total > 0) {
                        double hitRate = (double) hits / total * 100;
                        converted.put("hitRate", Math.round(hitRate));
                    }

                    stats.put(cacheType, converted);
                }
            }

            // 전체 캐시 크기
            stats.put("totalKeys", countCacheKeys());

        } catch (Exception e) {
            log.error("캐시 통계 조회 실패", e);
        }

        return stats;
    }

    /**
     * 캐시 키 카운트
     */
    private long countCacheKeys() {
        try {
            Set<String> keys = redisTemplate.keys("rec:*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 캐시 워밍 - 인기 상품 미리 로드
     */
    public void warmCache(List<String> categories) {
        log.info("캐시 워밍 시작: categories={}", categories);

        categories.forEach(category -> {
            try {
                // 카테고리별 인기 상품을 미리 캐싱하는 로직
                // IntegratedRecommendationService에서 호출 가능
                log.debug("카테고리 캐시 워밍: {}", category);
            } catch (Exception e) {
                log.error("캐시 워밍 실패: category={}", category, e);
            }
        });
    }
}