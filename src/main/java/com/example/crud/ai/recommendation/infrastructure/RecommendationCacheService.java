package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 *  추천 결과 캐싱 서비스
 *  - 사용자별 맞춤 추천 캐싱
 *  - 인기 상품 캐싱
 *  - 실시간 추천 업데이트
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

    /**
     * 사용자 맞춤 추천 캐싱
     */
    public void cacheUserRecommendations(Long userId, List<ProductResponseDto> products) {
        try {
            String key = USER_RECOMMENDATION_KEY + userId;
            String json = objectMapper.writeValueAsString(products);

            redisTemplate.opsForValue().set(key, json, 6, TimeUnit.HOURS);

            // 추천 히스토리 추가 (최근 10개만 유지)
            String historyKey = key + ":history";
            redisTemplate.opsForList().leftPush(historyKey, json);
            redisTemplate.opsForList().trim(historyKey, 0, 9);
            redisTemplate.expire(historyKey, Duration.ofDays(7));

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
                return Optional.of(products);
            }
        } catch (Exception e) {
            log.error("추천 캐시 조회 실패: userId={}", userId, e);
        }
        return Optional.empty();
    }

    /**
     * 카테고리별 인기 상품 캐싱
     */
    public void cacheCategoryPopularProducts(String category, List<ProductResponseDto> products) {
        try {
            String key = CATEGORY_POPULAR_KEY + category;

            // Sorted Set으로 저장 (점수 = 판매량 또는 조회수)
            products.forEach(product -> {
                String productJson = toJson(product);
                double score = calculatePopularityScore(product);
                redisTemplate.opsForZSet().add(key, productJson, score);
            });

            // 상위 50개만 유지
            redisTemplate.opsForZSet().removeRange(key, 0, -51);
            redisTemplate.expire(key, Duration.ofHours(12));

        } catch (Exception e) {
            log.error("인기 상품 캐싱 실패: category={}", category, e);
        }
    }

    /**
     * 실시간 트렌딩 상품 업데이트
     */
    public void updateTrendingProducts(Long productId, double incrementScore) {
        try {
            String product = getProductJson(productId);
            if (product != null) {
                redisTemplate.opsForZSet().incrementScore(TRENDING_KEY, product, incrementScore);

                // 상위 100개만 유지
                Long size = redisTemplate.opsForZSet().size(TRENDING_KEY);
                if (size != null && size > 100) {
                    redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, size - 101);
                }
            }
        } catch (Exception e) {
            log.error("트렌딩 업데이트 실패: productId={}", productId, e);
        }
    }

    /**
     * 유사 상품 캐싱
     */
    public void cacheSimilarProducts(Long productId, List<ProductResponseDto> similarProducts) {
        try {
            String key = SIMILAR_PRODUCT_KEY + productId;
            String json = objectMapper.writeValueAsString(similarProducts);

            redisTemplate.opsForValue().set(key, json, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("유사 상품 캐싱 실패: productId={}", productId, e);
        }
    }

    /**
     * 추천 캐시 워밍
     * - 애플리케이션 시작 시 또는 주기적으로 실행
     */
    public void warmUpCache() {
        log.info("추천 캐시 워밍 시작");

        try {
            // 카테고리별 인기 상품 로드
            Arrays.asList("TOP", "OUTER", "BOTTOM", "DRESS", "BAG", "SHOES", "ACCESSORY")
                    .forEach(this::loadCategoryPopularProducts);

            // 전체 트렌딩 상품 로드
            loadTrendingProducts();

            log.info("추천 캐시 워밍 완료");
        } catch (Exception e) {
            log.error("캐시 워밍 실패", e);
        }
    }

    private double calculatePopularityScore(ProductResponseDto product) {
        // 실제로는 판매량, 조회수, 좋아요 수 등을 고려한 복합 점수 계산
        return Math.random() * 1000; // 임시 구현
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getProductJson(Long productId) {
        // 실제로는 DB에서 조회 후 JSON 변환
        return null; // 임시 구현
    }

    private void loadCategoryPopularProducts(String category) {
        // 실제로는 DB에서 카테고리별 인기 상품 조회
    }

    private void loadTrendingProducts() {
        // 실제로는 DB에서 트렌딩 상품 조회
    }
}
