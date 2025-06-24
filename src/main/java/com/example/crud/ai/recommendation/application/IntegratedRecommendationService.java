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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 통합 상품 추천 서비스 (개선된 버전)
 * - 중복 코드 제거 및 메서드 통합
 * - 사용되지 않는 메서드 제거
 * - 에러 처리 개선
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
    private static final int VECTOR_DIMENSION = 384; // ML 서비스와 일치
    private static final String METRICS_KEY = "recommendation:metrics:";
    private static final String TRENDING_KEY = "recommendation:trending:products";

    /**
     * 메인 추천 메서드 - 모든 추천 요청의 진입점
     */
    public List<ProductResponseDto> recommend(Long userId, String message) {
        log.debug("추천 요청: userId={}, message={}", userId, message);

        try {
            // 비회원 처리
            if (userId == null) {
                return recommendForGuest(message);
            }

            // 캐시 확인
            Optional<List<ProductResponseDto>> cached = cacheService.getCachedUserRecommendations(userId);
            if (cached.isPresent() && !isRefreshRequired(message)) {
                log.debug("캐시된 추천 반환: userId={}", userId);
                recordMetrics("cache_hit", userId);
                return cached.get();
            }

            // 추천 생성
            List<ProductResponseDto> recommendations = generateRecommendations(userId, message);

            // 캐싱 및 이벤트 발행
            CompletableFuture.runAsync(() -> {
                cacheService.cacheUserRecommendations(userId, recommendations);
                publishRecommendationEvent(userId, recommendations);
            });

            recordMetrics("recommendation_generated", userId);
            return recommendations;

        } catch (Exception e) {
            log.error("추천 생성 실패: userId={}, error={}", userId, e.getMessage(), e);
            return getFallbackRecommendations(userId);
        }
    }

    /**
     * 대화 컨텍스트를 포함한 추천
     */
    public List<ProductResponseDto> recommendWithContext(Long userId, String message, List<String> context) {
        String combinedMessage = buildContextualMessage(context, message);
        return recommend(userId, combinedMessage);
    }

    /**
     * 카테고리 기반 추천
     */
    public List<ProductResponseDto> recommendByCategory(String categoryName, int limit) {
        String cacheKey = "rec:category:" + categoryName;

        try {
            // 캐시 확인
            List<ProductResponseDto> cached = getCachedRecommendations(cacheKey);
            if (!cached.isEmpty()) {
                return cached.stream().limit(limit).collect(Collectors.toList());
            }

            // 카테고리 추천 생성
            Category category = resolveCategory(categoryName);
            List<Product> products = productRepository.findByCategory(category);

            List<ProductResponseDto> recommendations = products.stream()
                    .limit(limit)
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            // 캐싱
            cacheRecommendations(cacheKey, recommendations, Duration.ofHours(1));

            return recommendations;

        } catch (Exception e) {
            log.error("카테고리 추천 실패: category={}", categoryName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 통합 추천 생성 로직 (개선된 버전)
     */
    private List<ProductResponseDto> generateRecommendations(Long userId, String message) {
        // 1. 임베딩 생성
        float[] queryVector = getOrCreateEmbedding(message);
        if (queryVector == null || queryVector.length != VECTOR_DIMENSION) {
            log.warn("임베딩 생성 실패 또는 차원 불일치");
            return getFallbackRecommendations(userId);
        }

        // 2. 사용자 선호도 조회
        Map<String, Object> preferences = getUserPreferences(userId);

        // 3. 점수 계산
        Map<Long, Double> scoreMap = new HashMap<>();

        // 벡터 유사도 (40%)
        addVectorSimilarityScores(scoreMap, queryVector, 0.4);

        // 사용자 선호도 (30%)
        if (!preferences.isEmpty()) {
            addPreferenceScores(scoreMap, preferences, 0.3);
        }

        // 트렌드 (20%)
        addTrendingScores(scoreMap, 0.2);

        // 협업 필터링 (10%)
        addCollaborativeScores(scoreMap, userId, 0.1);

        // 4. 상위 N개 선택
        return selectTopProducts(scoreMap, DEFAULT_RECOMMENDATION_SIZE);
    }

    /**
     * 벡터 유사도 점수 추가
     */
    private void addVectorSimilarityScores(Map<Long, Double> scoreMap, float[] queryVector, double weight) {
        try {
            List<ProductMatch> matches = vectorRepository.findTopN(queryVector, 50);
            for (ProductMatch match : matches) {
                if (match.score() > 0.5) { // 최소 유사도 임계값
                    scoreMap.merge(match.id(), match.score() * weight, Double::sum);
                }
            }
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
                        // findByCategory는 Category 파라미터 하나만 받음!
                        List<Product> categoryProducts = productRepository.findByCategory(category);

                        // stream으로 필터링 및 제한
                        List<Product> filteredProducts = categoryProducts.stream()
                                .filter(p -> p.getDescriptionVector() != null)
                                .limit(100)  // 성능을 위해 제한
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
                // 브랜드로 필터링하여 조회
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
     * 협업 필터링 점수 추가
     */
    private void addCollaborativeScores(Map<Long, Double> scoreMap, Long userId, double weight) {
        try {
            String userKey = "collaborative:user:" + userId;
            Map<Object, Object> userProducts = redisTemplate.opsForHash().entries(userKey);

            for (Map.Entry<Object, Object> entry : userProducts.entrySet()) {
                Long productId = Long.parseLong(entry.getKey().toString());
                Double score = Double.parseDouble(entry.getValue().toString());
                scoreMap.merge(productId, score * weight, Double::sum);
            }
        } catch (Exception e) {
            log.debug("협업 필터링 점수 계산 실패: {}", e.getMessage());
        }
    }

    /**
     * 임베딩 생성 또는 캐시 조회 (개선된 버전)
     */
    private float[] getOrCreateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            return embeddingClient.embed(text)
                    .block(Duration.ofSeconds(3));
        } catch (Exception e) {
            log.error("임베딩 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 사용자 선호도 조회
     */
    private Map<String, Object> getUserPreferences(Long userId) {
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

        // 가격 포맷팅 (int -> String)
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
            // 대문자로 변환하여 enum 매칭 시도
            return Category.valueOf(categoryName.toUpperCase());
        } catch (Exception e) {
            // 실패 시 모든 Category 값을 순회하며 매칭
            for (Category cat : Category.values()) {
                if (cat.name().equalsIgnoreCase(categoryName)) {
                    return cat;
                }
            }
            throw new IllegalArgumentException("알 수 없는 카테고리: " + categoryName);
        }
    }

    /**
     * 비회원 추천
     */
    private List<ProductResponseDto> recommendForGuest(String message) {
        try {
            float[] queryVector = getOrCreateEmbedding(message);
            if (queryVector == null) {
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
     * 인기 상품 조회 (폴백)
     */
    private List<ProductResponseDto> getPopularProducts() {
        // 최신 상품 20개를 인기 상품으로 간주 (실제로는 판매량, 조회수 등의 기준 필요)
        List<Product> products = productRepository.findTop20ByOrderByNumberDesc();
        return products.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
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
     * ProductMatch를 DTO로 변환
     */
    private List<ProductResponseDto> convertMatchesToDtos(List<ProductMatch> matches) {
        if (matches.isEmpty()) {
            return new ArrayList<>();
        }

        // Product ID 목록 추출
        List<Long> productIds = matches.stream()
                .map(ProductMatch::id)
                .collect(Collectors.toList());

        // 상품 정보 일괄 조회
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getNumber, p -> p));

        // 매치 순서대로 DTO 변환
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
        // 특정 키워드가 포함된 경우 새로고침
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

        // 최근 3개 메시지만 사용
        int startIdx = Math.max(0, context.size() - 3);
        List<String> recentContext = context.subList(startIdx, context.size());

        return String.join(" ", recentContext) + " " + currentMessage;
    }

    /**
     * 캐시 저장
     */
    private void cacheRecommendations(String key, List<ProductResponseDto> recommendations, Duration ttl) {
        try {
            String json = Json.encode(recommendations);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.error("캐시 저장 실패: key={}", key, e);
        }
    }

    /**
     * 캐시 조회
     */
    private List<ProductResponseDto> getCachedRecommendations(String key) {
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Json.decode(cached.toString(), List.class);
            }
        } catch (Exception e) {
            log.error("캐시 조회 실패: key={}", key, e);
        }
        return new ArrayList<>();
    }

    /**
     * 추천 이벤트 발행
     */
    private void publishRecommendationEvent(Long userId, List<ProductResponseDto> recommendations) {
        try {
            Map<String, Object> event = Map.of(
                    "userId", userId,
                    "productIds", recommendations.stream()
                            .map(ProductResponseDto::getNumber)
                            .collect(Collectors.toList()),
                    "timestamp", LocalDateTime.now().toString()
            );

            kafkaTemplate.send("recommendation.generated", Json.encode(event));
        } catch (Exception e) {
            log.error("이벤트 발행 실패", e);
        }
    }

    /**
     * 메트릭 기록
     */
    private void recordMetrics(String metric, Long userId) {
        try {
            String key = METRICS_KEY + metric;
            redisTemplate.opsForValue().increment(key);

            // 일별 메트릭
            String dailyKey = key + ":" + LocalDateTime.now().toLocalDate();
            redisTemplate.opsForValue().increment(dailyKey);
            redisTemplate.expire(dailyKey, Duration.ofDays(7));
        } catch (Exception e) {
            log.debug("메트릭 기록 실패", e);
        }
    }
}