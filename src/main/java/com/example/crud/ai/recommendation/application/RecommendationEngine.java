package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService.ProductSimilarity;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.enums.Category;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 핵심 추천 엔진
 * - 벡터 기반 유사도 검색
 * - 사용자 선호도 기반 필터링
 * - 트렌드 기반 점수 조정
 * - 하이브리드 추천 알고리즘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEngine {

    private final EmbeddingApiClient embeddingApiClient;
    private final ProductVectorService vectorService;
    private final ProductRepository productRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${ai.cache.enabled:true}")
    private boolean cacheEnabled;

    // 추천 시스템 상수
    private static final int DEFAULT_RECOMMENDATION_SIZE = 20;
    private static final int VECTOR_DIMENSION = 384;
    private static final String TRENDING_KEY = "recommendation:trending:products";
    private static final String METRICS_KEY = "recommendation:metrics:";

    /**
     * ProductMatch 형태로 추천 결과 반환 (ConversationalRecommendationService용)
     */
    public List<ProductMatch> getRecommendations(String message, int limit) {
        try {
            // 1. 벡터 기반 유사 상품 찾기
            List<ProductSimilarity> vectorMatches = vectorService.findSimilarProducts(message, limit);
            
            // 2. ProductMatch 형태로 변환
            return vectorMatches.stream()
                    .map(similarity -> new ProductMatch(
                            similarity.productId(),
                            similarity.productName(),
                            similarity.similarity()
                    ))
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("추천 생성 실패: message={}", message, e);
            return List.of();
        }
    }

    /**
     * 기본 추천 생성
     */
    public List<ProductResponseDto> recommend(Long userId, String message) {
        return recommendWithContext(userId, message, new ArrayList<>());
    }

    /**
     * 컨텍스트 기반 추천 생성
     */
    public List<ProductResponseDto> recommendWithContext(Long userId, String message, List<String> context) {
        log.debug("추천 생성 시작: userId={}, message={}", userId, message);

        try {
            // 컨텍스트와 메시지 결합
            String combinedMessage = buildContextualMessage(context, message);

            // 1. 벡터 기반 유사 상품 찾기 (핵심 기능 복원)
            List<ProductSimilarity> vectorMatches = vectorService.findSimilarProducts(combinedMessage, 30);
            
            if (vectorMatches.isEmpty()) {
                log.warn("벡터 매칭 결과 없음, fallback 사용");
                return getFallbackRecommendations(userId);
            }

            // 2. 사용자 선호도 조회
            Map<String, Object> preferences = getUserPreferences(userId);

            // 3. 벡터 점수 + 선호도 점수 결합
            Map<Long, Double> scoreMap = new HashMap<>();
            
            // 벡터 유사도 (70%)
            for (ProductSimilarity match : vectorMatches) {
                scoreMap.put(match.productId(), match.similarity() * 0.7);
            }
            
            // 선호도 점수 (30%)
            if (!preferences.isEmpty()) {
                addPreferenceScores(scoreMap, preferences, 0.3);
            }

            // 4. 최종 추천 생성
            List<ProductResponseDto> recommendations = selectTopProducts(scoreMap, DEFAULT_RECOMMENDATION_SIZE);

            // 5. 캐싱
            if (cacheEnabled && userId != null && !recommendations.isEmpty()) {
                cacheService.cacheRecommendations(userId, recommendations);
            }

            log.info("추천 완료: userId={}, vectorMatches={}, finalRecommendations={}", 
                userId, vectorMatches.size(), recommendations.size());
                
            recordMetrics("recommendation_generated", userId);
            return recommendations;

        } catch (Exception e) {
            log.error("추천 생성 실패: userId={}, error={}", userId, e.getMessage(), e);
            return getFallbackRecommendations(userId);
        }
    }

    /**
     * 카테고리 기반 추천
     */
    public List<ProductResponseDto> recommendByCategory(String categoryName, int limit) {
        try {
            // 간단한 카테고리 추천

            // 카테고리 추천 생성
            Category category = resolveCategory(categoryName);
            List<Product> products = productRepository.findByCategory(category);

            List<ProductResponseDto> recommendations = products.stream()
                    .limit(limit)
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            // 결과 반환

            return recommendations;

        } catch (Exception e) {
            log.error("카테고리 추천 실패: category={}", categoryName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 하이브리드 추천 생성
     */
    private List<ProductResponseDto> generateHybridRecommendations(
            float[] queryVector, Map<String, Object> preferences, Long userId) {
        
        Map<Long, Double> scoreMap = new HashMap<>();

        // 벡터 유사도 (50%)
        addVectorSimilarityScores(scoreMap, queryVector, 0.5);

        // 사용자 선호도 (30%)
        if (!preferences.isEmpty()) {
            addPreferenceScores(scoreMap, preferences, 0.3);
        }

        // 트렌드 (20%)
        addTrendingScores(scoreMap, 0.2);

        // 상위 N개 선택
        return selectTopProducts(scoreMap, DEFAULT_RECOMMENDATION_SIZE);
    }

    /**
     * 벡터 유사도 점수 추가 - 실제 벡터 검색 사용
     */
    private void addVectorSimilarityScores(Map<Long, Double> scoreMap, float[] queryVector, double weight) {
        try {
            // 쿼리 텍스트로 유사한 상품 찾기 (더 효율적)
            String queryText = "사용자 요청"; // 실제로는 원본 텍스트 전달 필요
            List<ProductSimilarity> similarities = vectorService.findSimilarProducts(queryText, 50);
            
            for (ProductSimilarity similarity : similarities) {
                if (similarity.similarity() > 0.3) {
                    scoreMap.merge(similarity.productId(), similarity.similarity() * weight, Double::sum);
                }
            }
            
            log.debug("벡터 유사도 점수 추가: {} 개 상품", similarities.size());
        } catch (Exception e) {
            log.error("벡터 유사도 계산 실패", e);
        }
    }

    /**
     * 사용자 선호도 점수 추가
     */
    private void addPreferenceScores(Map<Long, Double> scoreMap, Map<String, Object> preferences, double weight) {
        try {
            List<String> categoryNames = (List<String>) preferences.getOrDefault("categories", new ArrayList<>());
            List<String> brands = (List<String>) preferences.getOrDefault("brands", new ArrayList<>());

            // 카테고리별 상품 조회
            if (!categoryNames.isEmpty()) {
                for (String categoryName : categoryNames) {
                    try {
                        Category category = resolveCategory(categoryName);
                        List<Product> categoryProducts = productRepository.findByCategory(category);

                        List<Product> filteredProducts = categoryProducts.stream()
                                .filter(p -> p.getDescriptionVector() != null)
                                .limit(100)
                                .collect(Collectors.toList());

                        for (Product product : filteredProducts) {
                            double score = calculatePreferenceScore(product, preferences);
                            scoreMap.merge(product.getNumber(), score * weight, Double::sum);
                        }
                    } catch (Exception e) {
                        log.debug("카테고리 처리 실패: {}", categoryName);
                    }
                }
            }

            // 브랜드별 추가 점수
            if (!brands.isEmpty()) {
                List<Product> allProducts = productRepository.findAll();
                for (Product product : allProducts) {
                    if (brands.contains(product.getBrand()) && product.getDescriptionVector() != null) {
                        scoreMap.merge(product.getNumber(), 0.1 * weight, Double::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.error("선호도 점수 계산 실패", e);
        }
    }

    /**
     * 트렌드 점수 추가
     */
    private void addTrendingScores(Map<Long, Double> scoreMap, double weight) {
        try {
            Set<Object> trendingIds = redisTemplate.opsForZSet()
                    .reverseRange(TRENDING_KEY, 0, 30);

            if (trendingIds != null) {
                for (Object idObj : trendingIds) {
                    String idStr = idObj.toString();
                    Long productId = Long.parseLong(idStr);
                    Double trendScore = redisTemplate.opsForZSet().score(TRENDING_KEY, idStr);
                    if (trendScore != null) {
                        scoreMap.merge(productId, (trendScore / 100.0) * weight, Double::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.error("트렌드 점수 계산 실패", e);
        }
    }


    /**
     * 사용자 선호도 조회
     */
    private Map<String, Object> getUserPreferences(Long userId) {
        if (userId == null) return new HashMap<>();
        
        try {
            UserPreference preference = preferenceRepository.findByMember_Number(userId).orElse(null);
            if (preference != null && preference.getPreferences() != null) {
                return Json.decode(preference.getPreferences(), Map.class);
            }
        } catch (Exception e) {
            log.error("사용자 선호도 조회 실패: userId={}", userId, e);
        }
        return new HashMap<>();
    }

    /**
     * 상위 상품 선택
     */
    private List<ProductResponseDto> selectTopProducts(Map<Long, Double> scoreMap, int limit) {
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    Product product = productRepository.findById(entry.getKey()).orElse(null);
                    if (product != null) {
                        ProductResponseDto dto = convertToDto(product);
                        dto.setRelevance(entry.getValue());
                        return dto;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Product 엔티티를 DTO로 변환
     */
    private ProductResponseDto convertToDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setNumber(product.getNumber());
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());

        // 가격 포맷팅
        NumberFormat formatter = NumberFormat.getInstance(Locale.KOREA);
        dto.setPrice(formatter.format(product.getPrice()) + "원");

        dto.setImageUrl(product.getImageUrl());
        dto.setIntro(product.getIntro());
        dto.setDescription(product.getDescription());

        // Category enum을 String으로 변환
        if (product.getCategory() != null) {
            dto.setCategory(product.getCategory().name());
        }
        dto.setSubCategory(product.getSubCategory());

        // ProductOption 변환
        if (product.getProductOptions() != null && !product.getProductOptions().isEmpty()) {
            List<ProductOptionDto> optionDtos = product.getProductOptions().stream()
                    .map(option -> ProductOptionDto.builder()
                            .id(option.getId())
                            .color(option.getColor())
                            .size(option.getSize())
                            .stock(option.getStock())
                            .build())
                    .collect(Collectors.toList());
            dto.setProductOptions(optionDtos);
        }

        return dto;
    }

    /**
     * 카테고리 이름을 Enum으로 변환
     */
    private Category resolveCategory(String categoryName) {
        try {
            return Category.valueOf(categoryName.toUpperCase());
        } catch (Exception e) {
            for (Category cat : Category.values()) {
                if (cat.name().equalsIgnoreCase(categoryName)) {
                    return cat;
                }
            }
            throw new IllegalArgumentException("알 수 없는 카테고리: " + categoryName);
        }
    }

    /**
     * 폴백 추천
     */
    private List<ProductResponseDto> getFallbackRecommendations(Long userId) {
        if (userId != null) {
            // 사용자의 최근 관심 카테고리 기반 추천
            Map<String, Object> preferences = getUserPreferences(userId);
            List<String> categories = (List<String>) preferences.getOrDefault("categories", new ArrayList<>());

            if (!categories.isEmpty()) {
                return recommendByCategory(categories.get(0), DEFAULT_RECOMMENDATION_SIZE);
            }
        }
        return getPopularProducts();
    }

    /**
     * 인기 상품 조회 (폴백)
     */
    private List<ProductResponseDto> getPopularProducts() {
        List<Product> products = productRepository.findTop20ByOrderByNumberDesc();
        return products.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 선호도 점수 계산
     */
    private double calculatePreferenceScore(Product product, Map<String, Object> preferences) {
        double score = 0.5; // 기본 점수

        // 가격 범위 확인
        Integer minPrice = (Integer) preferences.get("minPrice");
        Integer maxPrice = (Integer) preferences.get("maxPrice");

        if (minPrice != null && maxPrice != null) {
            if (product.getPrice() >= minPrice && product.getPrice() <= maxPrice) {
                score += 0.3;
            }
        }

        // 선호 브랜드 확인
        List<String> brands = (List<String>) preferences.getOrDefault("brands", new ArrayList<>());
        if (brands.contains(product.getBrand())) {
            score += 0.2;
        }

        return Math.min(score, 1.0);
    }

    /**
     * 새로고침 필요 여부 판단
     */
    private boolean isRefreshRequired(String message) {
        String[] refreshKeywords = {"새로운", "다른", "추가", "더", "변경"};
        String lowerMessage = message.toLowerCase();

        return Arrays.stream(refreshKeywords)
                .anyMatch(lowerMessage::contains);
    }

    /**
     * 컨텍스트 메시지 생성
     */
    private String buildContextualMessage(List<String> context, String currentMessage) {
        if (context == null || context.isEmpty()) {
            return currentMessage;
        }

        int startIdx = Math.max(0, context.size() - 3);
        List<String> recentContext = context.subList(startIdx, context.size());

        return String.join(" ", recentContext) + " " + currentMessage;
    }


    /**
     * 메트릭 기록
     */
    private void recordMetrics(String metric, Long userId) {
        try {
            String key = METRICS_KEY + metric;
            redisTemplate.opsForValue().increment(key);

            String dailyKey = key + ":" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(dailyKey);
            redisTemplate.expire(dailyKey, Duration.ofDays(7));
        } catch (Exception e) {
            log.debug("메트릭 기록 실패", e);
        }
    }
}