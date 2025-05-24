package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.ConversationMessageRepository;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.embedding.domain.repository.ProductVectorRepository;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.enums.Category;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 통합 추천 서비스
 * - 벡터 유사도 기반 추천
 * - 사용자 선호도 기반 추천
 * - 협업 필터링
 * - 실시간 트렌드 반영
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedRecommendationService {

    private final EmbeddingClient embeddingClient;
    private final ProductRepository productRepository;
    private final ProductVectorRepository vectorRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final ConversationMessageRepository messageRepository;
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PREFERENCE_CACHE_PREFIX = "user:preference:";

    /**
     * 통합 추천 메서드
     * 1. 캐시 확인
     * 2. 사용자 선호도 조회
     * 3. 벡터 유사도 검색
     * 4. 개인화 점수 계산
     * 5. 결과 캐싱
     */
    public List<ProductResponseDto> recommendForUser(Long userId, String userMessage) {
        // 1. 캐시 확인
        Optional<List<ProductResponseDto>> cached = cacheService.getCachedUserRecommendations(userId);
        if (cached.isPresent() && !userMessage.contains("새로운") && !userMessage.contains("다른")) {
            log.debug("캐시된 추천 사용: userId={}", userId);
            return cached.get();
        }

        try {
            // 2. 사용자 선호도 조회
            Map<String, Object> userPreferences = getUserPreferences(userId);

            // 3. 메시지 임베딩 생성
            float[] queryVector = embeddingClient.embed(userMessage)
                    .block(Duration.ofSeconds(3));

            if (queryVector == null || queryVector.length == 0) {
                log.warn("임베딩 생성 실패, 기본 추천으로 대체");
                return getDefaultRecommendations(userPreferences);
            }

            // 4. 다중 전략 추천
            List<ProductResponseDto> recommendations = multiStrategyRecommend(
                    userId, queryVector, userPreferences, userMessage
            );

            // 5. 결과 캐싱
            cacheService.cacheUserRecommendations(userId, recommendations);

            // 6. 추천 이벤트 발행 (분석용)
            publishRecommendationEvent(userId, recommendations);

            return recommendations;

        } catch (Exception e) {
            log.error("추천 생성 실패: userId={}", userId, e);
            return getFallbackRecommendations();
        }
    }

    /**
     * 다중 전략 기반 추천
     */
    private List<ProductResponseDto> multiStrategyRecommend(
            Long userId, float[] queryVector, Map<String, Object> preferences, String message) {

        Map<Long, Double> scoreMap = new HashMap<>();

        // 1. 벡터 유사도 기반 추천 (40%)
        List<ProductMatch> vectorMatches = vectorRepository.findTopN(queryVector, 50);
        for (ProductMatch match : vectorMatches) {
            scoreMap.merge(match.id(), match.score() * 0.4, Double::sum);
        }

        // 2. 카테고리 선호도 기반 추천 (30%)
        List<String> preferredCategories = getPreferredCategories(preferences);
        if (!preferredCategories.isEmpty()) {
            List<Product> categoryProducts = productRepository
                    .findByCategoryInAndDescriptionVectorIsNotNull(preferredCategories);

            for (Product product : categoryProducts) {
                double categoryScore = calculateCategoryScore(product, preferences);
                scoreMap.merge(product.getNumber(), categoryScore * 0.3, Double::sum);
            }
        }

        // 3. 가격대 필터링 (10%)
        Map<String, Integer> priceRange = getPriceRange(preferences);
        scoreMap.forEach((productId, score) -> {
            Product product = productRepository.findById(productId).orElse(null);
            if (product != null) {
                double priceScore = calculatePriceScore(product.getPrice(), priceRange);
                scoreMap.put(productId, score + priceScore * 0.1);
            }
        });

        // 4. 트렌딩 가산점 (10%)
        Set<Long> trendingIds = getTrendingProductIds();
        scoreMap.forEach((productId, score) -> {
            if (trendingIds.contains(productId)) {
                scoreMap.put(productId, score + 0.1);
            }
        });

        // 5. 협업 필터링 (10%)
        Map<Long, Double> collaborativeScores = getCollaborativeScores(userId);
        collaborativeScores.forEach((productId, cfScore) -> {
            scoreMap.merge(productId, cfScore * 0.1, Double::sum);
        });

        // 최종 정렬 및 상위 N개 선택
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(20)
                .map(entry -> {
                    Product product = productRepository.findById(entry.getKey()).orElse(null);
                    if (product != null) {
                        ProductResponseDto dto = mapToDto(product);
                        dto.setRelevance(entry.getValue());
                        return dto;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 사용자 선호도 조회 (캐시 우선)
     */
    private Map<String, Object> getUserPreferences(Long userId) {
        try {
            // Redis 캐시 확인
            String cacheKey = PREFERENCE_CACHE_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                return Json.decode(cached.toString(), Map.class);
            }

            // DB 조회
            Optional<UserPreference> preference = preferenceRepository
                    .findByMember_Number(userId);

            if (preference.isPresent()) {
                Map<String, Object> prefs = Json.decode(
                        preference.get().getPreferences(), Map.class
                );

                // 캐시 저장
                redisTemplate.opsForValue().set(cacheKey, Json.encode(prefs),
                        Duration.ofHours(6));

                return prefs;
            }
        } catch (Exception e) {
            log.error("선호도 조회 실패", e);
        }

        return new HashMap<>();
    }

    /**
     * 추천 이벤트 발행 (분석 및 A/B 테스트용)
     */
    private void publishRecommendationEvent(Long userId, List<ProductResponseDto> recommendations) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("timestamp", System.currentTimeMillis());
            event.put("recommendationCount", recommendations.size());
            event.put("productIds", recommendations.stream()
                    .map(ProductResponseDto::getNumber)
                    .limit(10)
                    .collect(Collectors.toList()));

            // Kafka로 이벤트 발행 (추후 구현)
            // kafkaTemplate.send("recommendation-events", Json.encode(event));

        } catch (Exception e) {
            log.error("추천 이벤트 발행 실패", e);
        }
    }

    // === 헬퍼 메서드들 ===

    // getPreferredCategories 메서드에서 카테고리 문자열 처리
    private List<String> getPreferredCategories(Map<String, Object> preferences) {
        Object categories = preferences.get("categories");
        if (categories instanceof List) {
            // 카테고리가 한글 이름으로 저장된 경우 Enum name으로 변환
            return ((List<String>) categories).stream()
                    .map(cat -> {
                        try {
                            // "상의" → "TOP" 변환
                            return Category.fromGroupName(cat).name();
                        } catch (Exception e) {
                            // 이미 Enum name인 경우 그대로 사용
                            return cat;
                        }
                    })
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private Map<String, Integer> getPriceRange(Map<String, Object> preferences) {
        Map<String, Integer> range = new HashMap<>();
        range.put("min", 0);
        range.put("max", Integer.MAX_VALUE);

        Object priceRange = preferences.get("priceRange");
        if (priceRange instanceof Map) {
            Map<String, Object> pr = (Map<String, Object>) priceRange;
            range.put("min", (Integer) pr.getOrDefault("min", 0));
            range.put("max", (Integer) pr.getOrDefault("max", Integer.MAX_VALUE));
        }

        return range;
    }

    private double calculateCategoryScore(Product product, Map<String, Object> preferences) {
        double score = 0.0;

        // 1. 카테고리 일치도
        List<String> preferredCategories = (List<String>) preferences.getOrDefault("categories", new ArrayList<>());
        if (preferredCategories.contains(product.getCategory().getGroupName())) {
            score += 0.4;
        }

        // 2. 스타일 일치도
        List<String> preferredStyles = (List<String>) preferences.getOrDefault("styles", new ArrayList<>());
        String productDescription = product.getDescription().toLowerCase();
        for (String style : preferredStyles) {
            if (productDescription.contains(style.toLowerCase())) {
                score += 0.2;
                break;
            }
        }

        // 3. 색상 일치도
        List<String> preferredColors = (List<String>) preferences.getOrDefault("colors", new ArrayList<>());
        Set<String> productColors = product.getProductOptions().stream()
                .map(opt -> opt.getColor().toLowerCase())
                .collect(Collectors.toSet());

        for (String color : preferredColors) {
            if (productColors.contains(color.toLowerCase())) {
                score += 0.2;
                break;
            }
        }

        // 4. 키워드 매칭
        List<String> keywords = (List<String>) preferences.getOrDefault("keywords", new ArrayList<>());
        int matchedKeywords = 0;
        for (String keyword : keywords) {
            if (productDescription.contains(keyword.toLowerCase()) ||
                    product.getName().toLowerCase().contains(keyword.toLowerCase())) {
                matchedKeywords++;
            }
        }
        score += Math.min(0.2, matchedKeywords * 0.05);

        return Math.min(1.0, score);
    }

    private double calculatePriceScore(Integer price, Map<String, Integer> priceRange) {
        if (price == null) return 0.3;

        int min = priceRange.get("min");
        int max = priceRange.get("max");

        // 가격이 정확히 범위 내에 있으면 만점
        if (price >= min && price <= max) {
            // 범위 중앙에 가까울수록 높은 점수
            int mid = (min + max) / 2;
            double deviation = Math.abs(price - mid) / (double)(max - min);
            return 1.0 - (deviation * 0.3); // 0.7 ~ 1.0
        }

        // 범위를 벗어난 경우
        if (price < min) {
            // 10% 이내면 부분 점수
            double underPercent = (double)(min - price) / min;
            if (underPercent <= 0.1) return 0.6;
            else if (underPercent <= 0.2) return 0.4;
            else return 0.2;
        } else {
            // 가격이 높은 경우
            double overPercent = (double)(price - max) / max;
            if (overPercent <= 0.1) return 0.5;
            else if (overPercent <= 0.2) return 0.3;
            else return 0.1;
        }
    }

    private Set<Long> getTrendingProductIds() {
        try {
            // Redis에서 트렌딩 상품 조회
            Set<Object> trendingSet = redisTemplate.opsForZSet()
                    .reverseRange("rec:trending", 0, 99);

            if (trendingSet == null || trendingSet.isEmpty()) {
                return new HashSet<>();
            }

            // JSON을 파싱하여 상품 ID 추출
            ObjectMapper mapper = new ObjectMapper();
            Set<Long> productIds = new HashSet<>();

            for (Object item : trendingSet) {
                try {
                    Map<String, Object> product = mapper.readValue(
                            item.toString(), Map.class
                    );
                    Long id = ((Number) product.get("number")).longValue();
                    productIds.add(id);
                } catch (Exception e) {
                    log.debug("트렌딩 상품 파싱 실패: {}", item);
                }
            }

            return productIds;
        } catch (Exception e) {
            log.error("트렌딩 상품 조회 실패", e);
            return new HashSet<>();
        }
    }

    private Map<Long, Double> getCollaborativeScores(Long userId) {
        Map<Long, Double> scores = new HashMap<>();

        try {
            // 1. 유사한 사용자 찾기
            List<Long> similarUsers = findSimilarUsersByPurchaseHistory(userId, 10);

            if (similarUsers.isEmpty()) {
                return scores;
            }

            // 2. 유사한 사용자들이 구매한 상품 조회
            Map<Long, Integer> productPurchaseCounts = new HashMap<>();
            for (Long similarUserId : similarUsers) {
                List<Long> purchasedProducts = getPurchasedProducts(similarUserId);
                for (Long productId : purchasedProducts) {
                    productPurchaseCounts.merge(productId, 1, Integer::sum);
                }
            }

            // 3. 현재 사용자가 이미 구매한 상품 제외
            Set<Long> userPurchasedProducts = new HashSet<>(getPurchasedProducts(userId));
            productPurchaseCounts.keySet().removeAll(userPurchasedProducts);

            // 4. 점수 계산 (구매 빈도 기반)
            int maxCount = productPurchaseCounts.values().stream()
                    .max(Integer::compareTo)
                    .orElse(1);

            productPurchaseCounts.forEach((productId, count) -> {
                double score = (double) count / maxCount;
                scores.put(productId, score);
            });

        } catch (Exception e) {
            log.error("협업 필터링 점수 계산 실패", e);
        }

        return scores;
    }

    private List<Long> findSimilarUsersByPurchaseHistory(Long userId, int limit) {
        // 실제로는 구매 이력 기반 코사인 유사도 계산
        // 여기서는 간단히 Redis에서 캐시된 결과 조회
        String key = "similar:users:" + userId;
        List<Object> cached = redisTemplate.opsForList().range(key, 0, limit - 1);

        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toList());
        }

        // 캐시가 없으면 계산 (실제로는 별도 배치 작업으로 처리)
        return new ArrayList<>();
    }

    private List<Long> getPurchasedProducts(Long userId) {
        // 실제로는 Orders 테이블에서 조회
        // 여기서는 캐시 조회
        String key = "user:purchased:" + userId;
        List<Object> cached = redisTemplate.opsForList().range(key, 0, -1);

        if (cached != null) {
            return cached.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private List<ProductResponseDto> getDefaultRecommendations(Map<String, Object> preferences) {
        try {
            // 선호 카테고리가 있으면 해당 카테고리의 인기 상품
            List<String> categories = (List<String>) preferences.get("categories");
            if (categories != null && !categories.isEmpty()) {
                String mainCategory = categories.get(0);

                // Redis에서 카테고리별 인기 상품 조회
                String key = "rec:popular:category:" + mainCategory;
                Set<Object> popularSet = redisTemplate.opsForZSet()
                        .reverseRange(key, 0, 19);

                if (popularSet != null && !popularSet.isEmpty()) {
                    return parseProductsFromCache(popularSet);
                }
            }

            // 전체 인기 상품
            return productRepository.findTop20ByOrderByIdDesc(PageRequest.of(0, 20)).stream()
                    .map(this::mapToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("기본 추천 조회 실패", e);
            return getFallbackRecommendations();
        }
    }

    private List<ProductResponseDto> getFallbackRecommendations() {
        // 최후의 수단: 최신 상품 20개
        return productRepository.findTop20ByDescriptionVectorIsNotNullOrderByNumberDesc()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private List<ProductResponseDto> parseProductsFromCache(Set<Object> cachedSet) {
        List<ProductResponseDto> products = new ArrayList<>();

        for (Object item : cachedSet) {
            try {
                // Redis에서 가져온 데이터가 JSON 문자열인 경우
                if (item instanceof String) {
                    Product product = Json.decode(item.toString(), Product.class);
                    products.add(mapToDto(product));
                }
                // 이미 역직렬화된 객체인 경우
                else if (item instanceof Product) {
                    products.add(mapToDto((Product) item));
                }
            } catch (Exception e) {
                log.debug("캐시 상품 파싱 실패: {}", item);
            }
        }

        return products;
    }

    private ProductResponseDto mapToDto(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setNumber(product.getNumber());
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());

        // price는 String 타입으로 변환
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        dto.setPrice(formatter.format(product.getPrice()) + "원");

        dto.setImageUrl(product.getImageUrl());
        dto.setIntro(product.getIntro());
        dto.setDescription(product.getDescription());
        dto.setCategory(product.getCategory().name());
        dto.setSubCategory(product.getSubCategory());

        // 옵션 정보는 필요 시 추가
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
}