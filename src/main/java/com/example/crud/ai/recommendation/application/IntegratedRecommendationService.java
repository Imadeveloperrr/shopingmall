package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.embedding.domain.repository.ProductVectorRepository;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import com.example.crud.enums.Category;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 통합 상품 추천 서비스
 *
 * - 벡터 유사도 기반 추천
 * - 사용자 선호도 기반 추천
 * - 협업 필터링
 * - 실시간 트렌드 반영
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class IntegratedRecommendationService {

    // Core dependencies
    private final EmbeddingClient embeddingClient;
    private final ProductRepository productRepository;
    private final ProductVectorRepository vectorRepository;
    private final UserPreferenceRepository preferenceRepository;

    // Cache & messaging
    private final RecommendationCacheService cacheService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Constants
    private static final int DEFAULT_RECOMMENDATION_SIZE = 20;
    private static final String PREFERENCE_CACHE_PREFIX = "user:preference:";
    private static final String EMBEDDING_CACHE_PREFIX = "embedding:";
    private static final String METRICS_KEY = "recommendation:metrics:";
    private static final String TRENDING_KEY = "recommendation:trending:products";
    private static final String COLLABORATIVE_KEY = "recommendation:collaborative:";

    /**
     * 메인 추천 메서드 - 모든 추천 요청의 진입점
     *
     * @param userId 사용자 ID (null 가능 - 비회원)
     * @param message 사용자 메시지 또는 검색어
     * @return 추천 상품 목록
     */
    public List<ProductResponseDto> recommend(Long userId, String message) {
        log.debug("추천 요청: userId={}, message={}", userId, message);

        // 1. 사용자 ID가 없는 경우 (비회원) 벡터 기반 추천만 제공
        if (userId == null) {
            return recommendForGuest(message);
        }

        // 2. 캐시 확인
        Optional<List<ProductResponseDto>> cached = cacheService.getCachedUserRecommendations(userId);
        if (cached.isPresent() && !isRefreshRequired(message)) {
            log.debug("캐시된 추천 반환: userId={}", userId);
            return cached.get();
        }

        try {
            // 3. 사용자 메시지 임베딩 생성
            float[] queryVector = getOrCreateEmbedding(message);
            if (queryVector == null || queryVector.length == 0) {
                log.warn("임베딩 생성 실패, 대체 추천 반환");
                return getFallbackRecommendations(userId);
            }

            // 4. 사용자 선호도 조회
            Map<String, Object> userPreferences = getUserPreferences(userId);

            // 5. 통합 추천 생성
            List<ProductResponseDto> recommendations = generateRecommendations(
                    userId, queryVector, userPreferences, message
            );

            // 6. 결과 후처리 (캐싱, 이벤트 발행)
            postProcessRecommendations(userId, recommendations);

            return recommendations;

        } catch (Exception e) {
            log.error("추천 생성 실패: userId={}, error={}", userId, e.getMessage(), e);
            return getFallbackRecommendations(userId);
        }
    }

    /**
     * 비회원을 위한 추천 (벡터 유사도만 사용)
     */
    private List<ProductResponseDto> recommendForGuest(String message) {
        try {
            float[] queryVector = getOrCreateEmbedding(message);
            if (queryVector == null || queryVector.length == 0) {
                return getPopularProducts();
            }

            List<ProductMatch> matches = vectorRepository.findTopN(queryVector, DEFAULT_RECOMMENDATION_SIZE);
            return convertMatchesToDtos(matches);

        } catch (Exception e) {
            log.error("비회원 추천 실패: {}", e.getMessage());
            return getPopularProducts();
        }
    }

    /**
     * 대화 컨텍스트를 포함한 추천
     * ConversationalRecommendationService에서 사용
     */
    public List<ProductResponseDto> recommendWithContext(Long userId, String message, List<String> context) {
        // 컨텍스트와 현재 메시지 결합
        String combinedContext = buildContextualMessage(context, message);
        return recommend(userId, combinedContext);
    }

    /**
     * 카테고리 기반 추천
     */
    public List<ProductResponseDto> recommendByCategory(String categoryName, int limit) {
        String cacheKey = "rec:category:" + categoryName;

        try {
            // 캐시 확인
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                List<Map<String, Object>> cachedList = Json.decode(cached.toString(), List.class);
                return cachedList.stream()
                        .map(map -> {
                            ProductResponseDto dto = new ProductResponseDto();
                            dto.setNumber(((Number) map.get("number")).longValue());
                            dto.setName((String) map.get("name"));
                            dto.setPrice((String) map.get("price"));
                            dto.setBrand((String) map.get("brand"));
                            dto.setImageUrl((String) map.get("imageUrl"));
                            dto.setIntro((String) map.get("intro"));
                            dto.setDescription((String) map.get("description"));
                            dto.setCategory((String) map.get("category"));
                            dto.setSubCategory((String) map.get("subCategory"));
                            if (map.get("relevance") != null) {
                                dto.setRelevance(((Number) map.get("relevance")).doubleValue());
                            }
                            return dto;
                        })
                        .collect(Collectors.toList());
            }

            // Category Enum으로 변환
            Category category = Category.fromGroupName(categoryName);

            // DB에서 조회
            List<Product> products = productRepository.findByCategory(category).stream()
                    .filter(p -> p.getDescriptionVector() != null)
                    .limit(limit)
                    .collect(Collectors.toList());

            List<ProductResponseDto> result = products.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            // 캐시 저장
            redisTemplate.opsForValue().set(cacheKey, Json.encode(result), 1, TimeUnit.HOURS);

            return result;

        } catch (Exception e) {
            log.error("카테고리 추천 실패: category={}", categoryName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 임베딩 생성 또는 캐시 조회
     */
    private float[] getOrCreateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String cacheKey = EMBEDDING_CACHE_PREFIX + text.hashCode();

        try {
            // Redis 캐시 확인
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Json.decode(cached.toString(), float[].class);
            }

            // ML 서비스에서 임베딩 생성
            float[] embedding = embeddingClient.embed(text)
                    .block(Duration.ofSeconds(3));

            if (embedding != null && embedding.length > 0) {
                // 캐시 저장 (비동기)
                redisTemplate.opsForValue().set(
                        cacheKey,
                        Json.encode(embedding),
                        Duration.ofHours(24)
                );
            }

            return embedding;

        } catch (Exception e) {
            log.error("임베딩 생성/조회 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 통합 추천 생성 로직
     * 여러 추천 방식을 조합하여 최종 점수 계산
     */
    private List<ProductResponseDto> generateRecommendations(
            Long userId,
            float[] queryVector,
            Map<String, Object> preferences,
            String message) {

        Map<Long, Double> scoreMap = new HashMap<>();

        // 1. 벡터 유사도 기반 추천 (40%)
        addVectorSimilarityScores(scoreMap, queryVector, 0.4);

        // 2. 사용자 선호도 기반 추천 (30%)
        addPreferenceScores(scoreMap, preferences, 0.3);

        // 3. 트렌드 기반 추천 (20%)
        addTrendingScores(scoreMap, 0.2);

        // 4. 협업 필터링 (10%)
        addCollaborativeScores(scoreMap, userId, 0.1);

        // 5. 가격 필터링 적용
        applyPriceFilter(scoreMap, preferences);

        // 6. 상위 N개 선택 및 DTO 변환
        return convertToProductDtos(scoreMap, DEFAULT_RECOMMENDATION_SIZE);
    }

    /**
     * 벡터 유사도 점수 추가
     */
    private void addVectorSimilarityScores(Map<Long, Double> scoreMap, float[] queryVector, double weight) {
        List<ProductMatch> matches = vectorRepository.findTopN(queryVector, 50);
        for (ProductMatch match : matches) {
            scoreMap.merge(match.id(), match.score() * weight, Double::sum);
        }
    }

    /**
     * 사용자 선호도 점수 추가
     */
    private void addPreferenceScores(Map<Long, Double> scoreMap, Map<String, Object> preferences, double weight) {
        List<String> categoryNames = (List<String>) preferences.getOrDefault("categories", new ArrayList<>());
        List<String> brands = (List<String>) preferences.getOrDefault("brands", new ArrayList<>());

        // 카테고리 이름을 Category Enum으로 변환
        List<Category> categories = categoryNames.stream()
                .map(name -> {
                    try {
                        return Category.fromGroupName(name);
                    } catch (Exception e) {
                        try {
                            return Category.valueOf(name);
                        } catch (Exception ex) {
                            return null;
                        }
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 카테고리별 상품 조회
        if (!categories.isEmpty()) {
            for (Category category : categories) {
                List<Product> categoryProducts = productRepository.findByCategory(category).stream()
                        .filter(p -> p.getDescriptionVector() != null)
                        .collect(Collectors.toList());

                for (Product product : categoryProducts) {
                    double score = calculatePreferenceScore(product, preferences);
                    scoreMap.merge(product.getNumber(), score * weight, Double::sum);
                }
            }
        }

        // 브랜드별 추가 점수
        if (!brands.isEmpty()) {
            for (Product product : productRepository.findAll()) {
                if (brands.contains(product.getBrand()) && product.getDescriptionVector() != null) {
                    scoreMap.merge(product.getNumber(), 0.2 * weight, Double::sum);
                }
            }
        }
    }

    /**
     * 트렌드 점수 추가
     */
    private void addTrendingScores(Map<Long, Double> scoreMap, double weight) {
        try {
            Set<Object> trendingSet = redisTemplate.opsForZSet()
                    .reverseRange(TRENDING_KEY, 0, 29);

            if (trendingSet != null) {
                for (Object productId : trendingSet) {
                    Long id = Long.parseLong(productId.toString());
                    Double score = redisTemplate.opsForZSet().score(TRENDING_KEY, productId);
                    if (score != null) {
                        scoreMap.merge(id, score * weight, Double::sum);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("트렌드 점수 조회 실패: {}", e.getMessage());
        }
    }

    /**
     * 협업 필터링 점수 추가
     */
    private void addCollaborativeScores(Map<Long, Double> scoreMap, Long userId, double weight) {
        try {
            String key = COLLABORATIVE_KEY + userId;
            Map<Object, Object> collaborativeMap = redisTemplate.opsForHash().entries(key);

            for (Map.Entry<Object, Object> entry : collaborativeMap.entrySet()) {
                Long productId = Long.parseLong(entry.getKey().toString());
                Double score = Double.parseDouble(entry.getValue().toString());
                scoreMap.merge(productId, score * weight, Double::sum);
            }
        } catch (Exception e) {
            log.warn("협업 필터링 점수 조회 실패: {}", e.getMessage());
        }
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

            // DB에서 조회
            UserPreference preference = preferenceRepository.findByMember_Number(userId)
                    .orElse(null);

            Map<String, Object> preferences = new HashMap<>();

            if (preference != null && preference.getPreferences() != null) {
                preferences = Json.decode(preference.getPreferences(), Map.class);
            } else {
                // 기본 선호도 설정
                preferences.put("categories", new ArrayList<>());
                preferences.put("brands", new ArrayList<>());
                preferences.put("priceRange", Map.of("min", 0, "max", 1000000));
                preferences.put("keywords", new ArrayList<>());
            }

            // 캐시 저장
            redisTemplate.opsForValue().set(cacheKey, Json.encode(preferences), 6, TimeUnit.HOURS);

            return preferences;

        } catch (Exception e) {
            log.error("선호도 조회 실패: userId={}", userId, e);
            return new HashMap<>();
        }
    }

    /**
     * 개별 상품의 선호도 점수 계산
     */
    private double calculatePreferenceScore(Product product, Map<String, Object> preferences) {
        double score = 0.0;

        // 카테고리 매칭
        List<String> categoryNames = (List<String>) preferences.getOrDefault("categories", new ArrayList<>());
        if (categoryNames.contains(product.getCategory().getGroupName()) ||
                categoryNames.contains(product.getCategory().name())) {
            score += 0.5;
        }

        // 브랜드 매칭
        List<String> brands = (List<String>) preferences.getOrDefault("brands", new ArrayList<>());
        if (brands.contains(product.getBrand())) {
            score += 0.3;
        }

        // 키워드 매칭
        List<String> keywords = (List<String>) preferences.getOrDefault("keywords", new ArrayList<>());
        String productText = (product.getName() + " " + product.getIntro() + " " + product.getDescription()).toLowerCase();
        for (String keyword : keywords) {
            if (productText.contains(keyword.toLowerCase())) {
                score += 0.1;
            }
        }

        return Math.min(score, 1.0); // 최대 1.0
    }

    /**
     * 가격 필터 적용
     */
    private void applyPriceFilter(Map<Long, Double> scoreMap, Map<String, Object> preferences) {
        Map<String, Integer> priceRange = (Map<String, Integer>)
                preferences.getOrDefault("priceRange", new HashMap<>());

        if (priceRange.isEmpty()) {
            return;
        }

        int minPrice = priceRange.getOrDefault("min", 0);
        int maxPrice = priceRange.getOrDefault("max", Integer.MAX_VALUE);

        // 상품 정보를 미리 로드
        Set<Long> productIds = new HashSet<>(scoreMap.keySet());
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getNumber, p -> p));

        scoreMap.forEach((productId, score) -> {
            Product product = productMap.get(productId);
            if (product != null) {
                int price = product.getPrice();

                if (price >= minPrice && price <= maxPrice) {
                    // 가격 범위 내: 보너스 점수
                    scoreMap.put(productId, score * 1.1);
                } else if (price < minPrice * 0.8 || price > maxPrice * 1.2) {
                    // 가격 범위 크게 벗어남: 페널티
                    scoreMap.put(productId, score * 0.5);
                }
            }
        });
    }

    /**
     * 점수 맵을 ProductResponseDto 리스트로 변환
     */
    private List<ProductResponseDto> convertToProductDtos(Map<Long, Double> scoreMap, int limit) {
        // 상품 ID 수집
        List<Long> topProductIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 상품 정보 일괄 조회
        Map<Long, Product> productMap = productRepository.findAllById(topProductIds)
                .stream()
                .collect(Collectors.toMap(Product::getNumber, p -> p));

        // DTO 변환 및 정렬
        return topProductIds.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(product -> {
                    ProductResponseDto dto = convertToDto(product);
                    dto.setRelevance(scoreMap.get(product.getNumber()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * ProductMatch 리스트를 DTO로 변환
     */
    private List<ProductResponseDto> convertMatchesToDtos(List<ProductMatch> matches) {
        Map<Long, Product> productMap = productRepository.findAllById(
                matches.stream().map(ProductMatch::id).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Product::getNumber, p -> p));

        return matches.stream()
                .map(match -> {
                    Product product = productMap.get(match.id());
                    if (product != null) {
                        ProductResponseDto dto = convertToDto(product);
                        dto.setRelevance(match.score());
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

        // 가격 포맷팅 (Integer -> String)
        NumberFormat formatter = NumberFormat.getInstance(Locale.KOREA);
        dto.setPrice(formatter.format(product.getPrice()) + "원");

        dto.setImageUrl(product.getImageUrl());
        dto.setIntro(product.getIntro());
        dto.setDescription(product.getDescription());
        dto.setCategory(product.getCategory().name());
        dto.setSubCategory(product.getSubCategory());

        // 옵션 변환
        if (product.getProductOptions() != null && !product.getProductOptions().isEmpty()) {
            List<ProductOptionDto> optionDtos = product.getProductOptions().stream()
                    .map(this::convertOptionToDto)
                    .collect(Collectors.toList());
            dto.setProductOptions(optionDtos);
        }

        return dto;
    }

    /**
     * ProductOption을 DTO로 변환
     */
    private ProductOptionDto convertOptionToDto(ProductOption option) {
        return ProductOptionDto.builder()
                .id(option.getId())
                .color(option.getColor())
                .size(option.getSize())
                .stock(option.getStock())
                .build();
    }

    /**
     * 추천 후처리
     */
    private void postProcessRecommendations(Long userId, List<ProductResponseDto> recommendations) {
        try {
            // 1. 캐시 저장
            cacheService.cacheUserRecommendations(userId, recommendations);

            // 2. 추천 이벤트 발행
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("timestamp", System.currentTimeMillis());
            event.put("recommendationCount", recommendations.size());
            event.put("productIds", recommendations.stream()
                    .map(ProductResponseDto::getNumber)
                    .limit(10)
                    .collect(Collectors.toList()));

            kafkaTemplate.send("recommendation-events", Json.encode(event));

            // 3. 메트릭 업데이트
            updateMetrics(userId, recommendations.size());

        } catch (Exception e) {
            log.warn("추천 후처리 중 오류: {}", e.getMessage());
            // 후처리 실패는 추천 결과에 영향을 주지 않음
        }
    }

    /**
     * 메트릭 업데이트
     */
    private void updateMetrics(Long userId, int recommendationCount) {
        try {
            String today = LocalDateTime.now().toLocalDate().toString();
            String metricsKey = METRICS_KEY + today;

            // 일별 추천 횟수
            redisTemplate.opsForHash().increment(metricsKey, "total_recommendations", 1);
            redisTemplate.opsForHash().increment(metricsKey, "user:" + userId, 1);

            // 추천 상품 수 통계
            redisTemplate.opsForHash().increment(metricsKey, "total_products", recommendationCount);

            // TTL 설정 (7일)
            redisTemplate.expire(metricsKey, 7, TimeUnit.DAYS);

        } catch (Exception e) {
            log.debug("메트릭 업데이트 실패: {}", e.getMessage());
        }
    }

    /**
     * 대체 추천 (폴백)
     */
    private List<ProductResponseDto> getFallbackRecommendations(Long userId) {
        try {
            // 1. 사용자 선호도 기반 기본 추천 시도
            if (userId != null) {
                Map<String, Object> preferences = getUserPreferences(userId);
                List<String> categories = (List<String>) preferences.get("categories");

                if (categories != null && !categories.isEmpty()) {
                    return recommendByCategory(categories.get(0), DEFAULT_RECOMMENDATION_SIZE);
                }
            }

            // 2. 인기 상품 반환
            return getPopularProducts();

        } catch (Exception e) {
            log.error("대체 추천 생성 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 인기 상품 조회
     */
    private List<ProductResponseDto> getPopularProducts() {
        try {
            Set<Object> popularSet = redisTemplate.opsForZSet()
                    .reverseRange("rec:popular:all", 0, DEFAULT_RECOMMENDATION_SIZE - 1);

            if (popularSet != null && !popularSet.isEmpty()) {
                List<Long> productIds = popularSet.stream()
                        .map(obj -> Long.parseLong(obj.toString()))
                        .collect(Collectors.toList());

                return productRepository.findAllById(productIds).stream()
                        .map(this::convertToDto)
                        .collect(Collectors.toList());
            }

            // Redis에 없으면 DB에서 직접 조회
            return productRepository.findTop20ByOrderByNumberDesc().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("인기 상품 조회 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 컨텍스트 메시지 생성
     */
    private String buildContextualMessage(List<String> context, String currentMessage) {
        if (context == null || context.isEmpty()) {
            return currentMessage;
        }

        // 최근 3개 메시지만 사용
        int contextSize = Math.min(3, context.size());
        List<String> recentContext = context.subList(Math.max(0, context.size() - contextSize), context.size());

        return String.join(" ", recentContext) + " " + currentMessage;
    }

    /**
     * 새로고침 필요 여부 확인
     */
    private boolean isRefreshRequired(String message) {
        if (message == null) return false;

        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("새로운") ||
                lowerMessage.contains("다른") ||
                lowerMessage.contains("추가") ||
                lowerMessage.contains("더");
    }
}