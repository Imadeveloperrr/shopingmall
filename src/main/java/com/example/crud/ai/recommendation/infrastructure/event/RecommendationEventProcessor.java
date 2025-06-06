package com.example.crud.ai.recommendation.infrastructure.event;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.embedding.domain.repository.ProductVectorRepository;
import com.example.crud.ai.es.service.ConversationSearchService;
import com.example.crud.ai.recommendation.application.EnhancedRecommendationService;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.infrastructure.RecommendationCacheService;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 이벤트 기반 추천 처리기
 * - 실시간 추천 업데이트
 * - 사용자 행동 추적
 * - A/B 테스트 지원
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class RecommendationEventProcessor {

    private final EnhancedRecommendationService recommendationService;
    private final RecommendationCacheService cacheService;
    private final ConversationSearchService searchService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ConversationRepository conversationRepository;
    private final ProductRepository productRepository;
    private final ProductVectorRepository productVectorRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 메시지 생성 이벤트 처리
     * - 실시간 추천 업데이트
     * - 트렌드 반영
     */
    @KafkaListener(topics = "conv-msg-created", groupId = "recommendation-processor")
    public void processMessageEvent(String json) {
        try {
            MsgCreatedPayload payload = Json.decode(json, MsgCreatedPayload.class);

            // 비동기로 추천 업데이트
            updateRecommendationsAsync(payload);

            // 트렌딩 키워드 업데이트
            updateTrendingKeywords(payload.content());

            // 실시간 분석 이벤트 발행
            publishAnalyticsEvent(payload);

        } catch (Exception e) {
            log.error("추천 이벤트 처리 실패", e);
        }
    }

    /**
     * 상품 조회 이벤트 처리
     */
    @KafkaListener(topics = "product-viewed", groupId = "recommendation-processor")
    public void processProductViewEvent(String json) {
        try {
            Map<String, Object> event = Json.decode(json, Map.class);
            Long productId = ((Number) event.get("productId")).longValue();
            Long userId = ((Number) event.get("userId")).longValue();

            // 조회 기반 트렌딩 점수 업데이트
            cacheService.updateTrendingProducts(productId, 1.0);

            // 유사 상품 추천 생성 및 캐싱
            generateAndCacheSimilarProducts(productId);

            // 사용자 행동 기록
            recordUserBehavior(userId, "view", productId);

        } catch (Exception e) {
            log.error("상품 조회 이벤트 처리 실패", e);
        }
    }

    /**
     * 구매 이벤트 처리
     */
    @KafkaListener(topics = "order-completed", groupId = "recommendation-processor")
    public void processOrderEvent(String json) {
        try {
            Map<String, Object> event = Json.decode(json, Map.class);
            Long userId = ((Number) event.get("userId")).longValue();
            List<Long> productIds = (List<Long>) event.get("productIds");

            // 구매 기반 트렌딩 점수 대폭 증가
            productIds.forEach(pid ->
                    cacheService.updateTrendingProducts(pid, 10.0)
            );

            // 사용자 추천 재계산 스케줄링
            scheduleRecommendationUpdate(userId);

            // 구매 패턴 분석
            analyzeAndStorePurchasePattern(userId, productIds);

        } catch (Exception e) {
            log.error("구매 이벤트 처리 실패", e);
        }
    }

    /**
     * 비동기 추천 업데이트
     */
    private void updateRecommendationsAsync(MsgCreatedPayload payload) {
        // 실제로는 @Async 메서드나 별도 스레드풀 사용
        new Thread(() -> {
            try {
                // 컨텍스트 구축
                String context = searchService.buildRecommendationContext(
                        payload.conversationId(), 10
                );

                // 새로운 추천 생성
                List<ProductResponseDto> newRecommendations =
                        recommendationService.recommendForUser(
                                getUserIdFromConversation(payload.conversationId()),
                                context
                        );

                // 추천 품질 평가
                double quality = evaluateRecommendationQuality(newRecommendations);

                if (quality > 0.7) { // 품질 임계값
                    // 캐시 업데이트
                    cacheService.cacheUserRecommendations(
                            getUserIdFromConversation(payload.conversationId()),
                            newRecommendations
                    );
                }

            } catch (Exception e) {
                log.error("비동기 추천 업데이트 실패", e);
            }
        }).start();
    }

    /**
     * 실시간 분석 이벤트 발행
     */
    private void publishAnalyticsEvent(MsgCreatedPayload payload) {
        Map<String, Object> analyticsEvent = new HashMap<>();
        analyticsEvent.put("eventType", "message_created");
        analyticsEvent.put("conversationId", payload.conversationId());
        analyticsEvent.put("messageType", payload.type());
        analyticsEvent.put("timestamp", System.currentTimeMillis());

        // 키워드 추출
        List<String> keywords = extractKeywords(payload.content());
        analyticsEvent.put("keywords", keywords);

        kafkaTemplate.send("analytics-events", Json.encode(analyticsEvent));
    }

    /**
     * 사용자 행동 기록
     */
    private void recordUserBehavior(Long userId, String action, Long productId) {
        Map<String, Object> behavior = new HashMap<>();
        behavior.put("userId", userId);
        behavior.put("action", action);
        behavior.put("productId", productId);
        behavior.put("timestamp", System.currentTimeMillis());

        // Redis에 저장 (시계열 데이터)
        String key = String.format("behavior:%d:%s", userId, action);
        // redisTemplate.opsForList().leftPush(key, Json.encode(behavior));

        // 행동 기반 추천 이벤트
        kafkaTemplate.send("user-behavior", Json.encode(behavior));
    }

    /**
     * 추천 품질 평가
     */
    private double evaluateRecommendationQuality(List<ProductResponseDto> recommendations) {
        if (recommendations.isEmpty()) return 0.0;

        // 다양성 점수
        double diversityScore = calculateDiversityScore(recommendations);

        // 관련성 점수 (평균 relevance)
        double relevanceScore = recommendations.stream()
                .mapToDouble(ProductResponseDto::getRelevance)
                .average()
                .orElse(0.0);

        // 가격 분포 점수
        double priceDistributionScore = calculatePriceDistributionScore(recommendations);

        return (diversityScore * 0.3 + relevanceScore * 0.5 + priceDistributionScore * 0.2);
    }

    // === 헬퍼 메서드들 ===

    private void updateTrendingKeywords(String content) {
        try {
            // 키워드 추출
            List<String> keywords = extractKeywords(content);

            // Redis에 트렌딩 키워드 업데이트
            String trendingKey = "trending:keywords:" + LocalDate.now();

            for (String keyword : keywords) {
                redisTemplate.opsForZSet().incrementScore(trendingKey, keyword, 1.0);
            }

            // 24시간 후 만료
            redisTemplate.expire(trendingKey, Duration.ofDays(1));

            // 상위 100개만 유지
            Long size = redisTemplate.opsForZSet().size(trendingKey);
            if (size != null && size > 100) {
                redisTemplate.opsForZSet().removeRange(trendingKey, 0, size - 101);
            }

        } catch (Exception e) {
            log.error("트렌딩 키워드 업데이트 실패", e);
        }
    }

    private void generateAndCacheSimilarProducts(Long productId) {
        try {
            // 상품 정보 조회
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null || product.getDescriptionVector() == null) {
                return;
            }

            // 유사 상품 검색
            List<ProductMatch> similarMatches = productVectorRepository
                    .findTopNByCategory(product.getDescriptionVector(),
                            product.getCategory().name(),
                            20);

            // 자기 자신 제외
            List<ProductResponseDto> similarProducts = similarMatches.stream()
                    .filter(match -> !match.id().equals(productId))
                    .limit(10)
                    .map(match -> {
                        Product p = productRepository.findById(match.id()).orElse(null);
                        if (p != null) {
                            ProductResponseDto dto = mapProductToDto(p);
                            dto.setRelevance(match.score());
                            return dto;
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 캐싱
            cacheService.cacheSimilarProducts(productId, similarProducts);

        } catch (Exception e) {
            log.error("유사 상품 생성 및 캐싱 실패: productId={}", productId, e);
        }
    }

    private void scheduleRecommendationUpdate(Long userId) {
        try {
            // 추천 재계산 작업을 지연 실행 (5분 후)
            String jobKey = "rec:update:job:" + userId;

            // 이미 스케줄된 작업이 있는지 확인
            Boolean exists = redisTemplate.hasKey(jobKey);
            if (Boolean.TRUE.equals(exists)) {
                return; // 이미 예약됨
            }

            // 작업 예약
            Map<String, Object> job = new HashMap<>();
            job.put("userId", userId);
            job.put("scheduledAt", System.currentTimeMillis());
            job.put("executeAt", System.currentTimeMillis() + 300000); // 5분 후

            redisTemplate.opsForValue().set(jobKey, job, Duration.ofMinutes(10));

            // 별도 스레드에서 실행
            CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES).execute(() -> {
                try {
                    // 사용자 추천 재계산
                    List<ProductResponseDto> newRecommendations =
                            recommendationService.recommendForUser(userId, "");

                    // 캐시 업데이트
                    cacheService.cacheUserRecommendations(userId, newRecommendations);

                    // 작업 완료 후 키 삭제
                    redisTemplate.delete(jobKey);

                } catch (Exception e) {
                    log.error("추천 업데이트 실패: userId={}", userId, e);
                }
            });

        } catch (Exception e) {
            log.error("추천 업데이트 스케줄링 실패: userId={}", userId, e);
        }
    }

    private void analyzeAndStorePurchasePattern(Long userId, List<Long> productIds) {
        try {
            // 구매한 상품들의 공통 특성 분석
            List<Product> products = productRepository.findAllById(productIds);

            Map<String, Object> pattern = new HashMap<>();

            // 1. 카테고리 분포
            Map<String, Long> categoryCount = products.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getCategory().getGroupName(),
                            Collectors.counting()
                    ));
            pattern.put("categories", categoryCount);

            // 2. 가격대 분석
            IntSummaryStatistics priceStats = products.stream()
                    .mapToInt(Product::getPrice)
                    .summaryStatistics();
            pattern.put("avgPrice", priceStats.getAverage());
            pattern.put("minPrice", priceStats.getMin());
            pattern.put("maxPrice", priceStats.getMax());

            // 3. 브랜드 선호도
            Map<String, Long> brandCount = products.stream()
                    .collect(Collectors.groupingBy(
                            Product::getBrand,
                            Collectors.counting()
                    ));
            pattern.put("brands", brandCount);

            // 4. 색상 선호도
            Map<String, Long> colorCount = products.stream()
                    .flatMap(p -> p.getProductOptions().stream())
                    .collect(Collectors.groupingBy(
                            ProductOption::getColor,
                            Collectors.counting()
                    ));
            pattern.put("colors", colorCount);

            // 5. 구매 시간 패턴
            pattern.put("purchaseTime", LocalDateTime.now());
            pattern.put("dayOfWeek", LocalDate.now().getDayOfWeek().name());

            // Redis에 저장 (시계열 데이터)
            String patternKey = "purchase:pattern:" + userId + ":" + System.currentTimeMillis();
            redisTemplate.opsForValue().set(patternKey, pattern, Duration.ofDays(90));

            // 사용자 구매 이력 업데이트
            String historyKey = "user:purchased:" + userId;
            for (Long productId : productIds) {
                redisTemplate.opsForList().leftPush(historyKey, productId.toString());
            }
            redisTemplate.expire(historyKey, Duration.ofDays(365));

            // 구매 패턴 이벤트 발행
            Map<String, Object> event = new HashMap<>();
            event.put("userId", userId);
            event.put("pattern", pattern);
            event.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send("purchase-pattern-analyzed", Json.encode(event));

        } catch (Exception e) {
            log.error("구매 패턴 분석 실패: userId={}", userId, e);
        }
    }

    private Long getUserIdFromConversation(Long conversationId) {
        try {
            // Redis 캐시 확인
            String cacheKey = "conv:user:" + conversationId;
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                return Long.parseLong(cached.toString());
            }

            // DB 조회
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElse(null);

            if (conversation != null && conversation.getMember() != null) {
                Long userId = conversation.getMember().getNumber();

                // 캐시 저장
                redisTemplate.opsForValue().set(cacheKey, userId.toString(),
                        Duration.ofHours(1));

                return userId;
            }

        } catch (Exception e) {
            log.error("대화에서 사용자 ID 조회 실패: conversationId={}", conversationId, e);
        }

        return null;
    }

    private List<String> extractKeywords(String content) {
        // 형태소 분석 기반 키워드 추출
        // 실제로는 KoNLPy 등의 형태소 분석기 사용

        List<String> keywords = new ArrayList<>();

        try {
            // 간단한 키워드 추출 (공백 기준 분리 + 필터링)
            String cleanContent = content.toLowerCase()
                    .replaceAll("[^가-힣a-z0-9\\s]", "");

            String[] words = cleanContent.split("\\s+");

            // 상품 관련 키워드 사전
            Set<String> productKeywords = Set.of(
                    // 카테고리
                    "상의", "하의", "아우터", "원피스", "가방", "신발", "악세서리",
                    // 스타일
                    "캐주얼", "포멀", "스포티", "빈티지", "모던", "클래식", "미니멀",
                    // 속성
                    "편안한", "따뜻한", "시원한", "가벼운", "부드러운", "세련된",
                    // 색상
                    "검정", "흰색", "회색", "네이비", "베이지", "카키", "데님",
                    // 상황
                    "데일리", "출근", "주말", "여행", "운동", "데이트", "모임"
            );

            for (String word : words) {
                if (word.length() > 1) {
                    // 사전에 있는 키워드
                    if (productKeywords.contains(word)) {
                        keywords.add(word);
                    }
                    // 브랜드명이나 특정 단어 패턴
                    else if (word.matches(".*[가-힣]+(룩|핏|스타일)$")) {
                        keywords.add(word);
                    }
                }
            }

        } catch (Exception e) {
            log.error("키워드 추출 실패", e);
        }

        return keywords.stream()
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private double calculateDiversityScore(List<ProductResponseDto> products) {
        if (products.size() < 2) return 0.0;

        try {
            // 1. 카테고리 다양성
            long uniqueCategories = products.stream()
                    .map(ProductResponseDto::getCategory)  // String 타입이므로 직접 사용
                    .distinct()
                    .count();
            double categoryDiversity = (double) uniqueCategories / products.size();

            // 2. 가격 분포 다양성
            List<Integer> prices = products.stream()
                    .map(p -> parsePrice(p.getPrice()))  // String을 Integer로 변환
                    .sorted()
                    .collect(Collectors.toList());

            double priceRange = prices.get(prices.size() - 1) - prices.get(0);
            double avgPrice = prices.stream().mapToInt(Integer::intValue).average().orElse(0);
            double priceDiversity = priceRange / (avgPrice + 1);

            // 3. 브랜드 다양성
            long uniqueBrands = products.stream()
                    .map(ProductResponseDto::getBrand)
                    .distinct()
                    .count();
            double brandDiversity = (double) uniqueBrands / products.size();

            // 가중 평균
            return (categoryDiversity * 0.4 +
                    Math.min(1.0, priceDiversity * 0.3) +
                    brandDiversity * 0.3);

        } catch (Exception e) {
            log.error("다양성 점수 계산 실패", e);
            return 0.5;
        }
    }

    private double calculatePriceDistributionScore(List<ProductResponseDto> products) {
        if (products.isEmpty()) return 0.0;

        try {
            // 가격 통계
            IntSummaryStatistics stats = products.stream()
                    .mapToInt(p -> parsePrice(p.getPrice()))  // String을 int로 변환
                    .summaryStatistics();

            double avg = stats.getAverage();
            double min = stats.getMin();
            double max = stats.getMax();

            // 표준편차 계산
            double variance = products.stream()
                    .mapToDouble(p -> Math.pow(parsePrice(p.getPrice()) - avg, 2))
                    .average()
                    .orElse(0.0);
            double stdDev = Math.sqrt(variance);

            // 변동계수 (CV)
            double cv = stdDev / avg;

            // CV가 0.3~0.5 사이일 때 최적 (적당한 가격 분포)
            if (cv >= 0.3 && cv <= 0.5) {
                return 1.0;
            } else if (cv < 0.3) {
                // 너무 균일함
                return 0.7;
            } else {
                // 너무 산발적
                return Math.max(0.5, 1.0 - (cv - 0.5));
            }

        } catch (Exception e) {
            log.error("가격 분포 점수 계산 실패", e);
            return 0.5;
        }
    }

    // 가격 문자열을 정수로 변환하는 헬퍼 메서드 추가
    private int parsePrice(String priceStr) {
        // "1,000원" -> 1000
        return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
    }

    private ProductResponseDto mapProductToDto(Product product) {
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