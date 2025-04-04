package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.dto.Preference;
import com.example.crud.data.ai.service.ChatGPTIntegrationService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.enums.Category;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 사용자 선호 정보를 기반으로 추천 상품을 산출하는 서비스
 * - 1단계: 카테고리 기준 후보 상품 조회 후, 허깅페이스에서 추출한 키워드로 추가 필터링
 * - 2단계: 초기 매칭 점수 산출 후, 심층 평가(비동기)를 통해 정밀 매칭 점수를 계산하여 가중합산
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final ProductRepository productRepository;
    private final ProductDescriptionAnalyzer descriptionAnalyzer;
    private final ChatGPTIntegrationService chatGPTIntegrationService;
    private final CandidateProductFilterService candidateProductFilterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 후보 필터링 시 최소 키워드 매칭 개수 (실무에서는 환경설정값으로 관리 가능)
    private static final int MIN_KEYWORD_MATCH = 2;

    /**
     * 사용자 선호 정보를 기반으로 추천을 수행합니다.
     * 1단계: 카테고리 기준으로 후보 상품 조회 후, 허깅페이스 키워드로 필터링
     * 2단계: 필터링된 후보에 대해 초기 점수 산출 및 ChatGPT 심층 평가를 비동기로 수행하여 가중 합산
     *
     * @param structuredPreference 사용자 선호 정보가 포함된 JSON 문자열
     * @return 추천 상품 목록 (최대 상위 10개)
     */
    public List<ProductResponseDto> getPersonalizedRecommendations(String structuredPreference) {
        // 사용자 선호 정보를 Preference 객체로 파싱
        Preference userPreference;
        try {
            userPreference = objectMapper.readValue(structuredPreference, Preference.class);
        } catch (Exception e) {
            log.error("사용자 선호 정보 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("사용자 선호 정보 파싱 실패: " + e.getMessage(), e);
        }

        // String으로 받은 카테고리를 Enum으로 변환
        Category categoryEnum = Category.fromGroupName(userPreference.getCategory());

        // 1. 카테고리 기준 후보 상품 조회  문제 (카테고리 기준으로 모든 데이터를 조회하기때문에 확인필요)
        List<Product> candidateProducts = productRepository.findByCategory(categoryEnum);
        log.info("카테고리 기준 후보 상품 수: {}", candidateProducts.size());

        // 2. 허깅페이스에서 추출한 키워드로 후보군 추가 필터링 (사용자 선호 스타일 기준)
        List<Product> filteredCandidates = candidateProductFilterService.filterCandidatesByKeywords(
                candidateProducts, userPreference.getStyle(), MIN_KEYWORD_MATCH);
        if (filteredCandidates.isEmpty()) {
            log.warn("사용자 선호 스타일 '{}'와 일치하는 후보 상품이 없습니다.", userPreference.getStyle());
            return Collections.emptyList();
        }

        // 3. 초기 매칭 점수 산출 (필터링된 후보 상품에 대해)
        Map<Product, Double> initialScoreMap = new HashMap<>();
        for (Product product : filteredCandidates) {
            List<String> productKeywords = descriptionAnalyzer.extractKeywords(product.getDescription());
            double initialScore = calculateInitialMatchingScore(userPreference, productKeywords);
            // 초기 점수가 0 이상인 상품만 후보로 선정
            if (initialScore > 0) {
                initialScoreMap.put(product, initialScore);
            }
        }
        log.info("초기 매칭 점수 산출 후 후보 상품 수: {}", initialScoreMap.size());

        if (initialScoreMap.isEmpty()) {
            log.warn("초기 매칭 점수가 0 이상인 후보 상품이 없습니다.");
            return Collections.emptyList();
        }

        // 4. 심층 평가(비동기): 각 후보 상품에 대해 ChatGPT를 호출하여 정밀 매칭 점수 산출
        List<CompletableFuture<Map.Entry<Product, Double>>> futureList = new ArrayList<>();
        for (Map.Entry<Product, Double> entry : initialScoreMap.entrySet()) {
            Product product = entry.getKey();
            double initialScore = entry.getValue();
            CompletableFuture<Map.Entry<Product, Double>> futureScore = deepMatchingScoreAsync(userPreference, product)
                    .thenApply(deepScore -> {
                        // 초기 점수 30%, 심층 평가 점수 70%의 가중 합산
                        double combinedScore = initialScore * 0.3 + deepScore * 0.7;
                        return new AbstractMap.SimpleEntry<>(product, combinedScore);
                    });
            futureList.add(futureScore);
        }
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0])).join();

        List<Map.Entry<Product, Double>> finalScoreList = futureList.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // 5. 최종 점수가 높은 순으로 정렬하여 상위 10개 상품을 추천
        List<ProductResponseDto> recommendations = finalScoreList.stream()
                .sorted(Map.Entry.<Product, Double>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    ProductResponseDto dto = new ProductResponseDto();
                    BeanUtils.copyProperties(entry.getKey(), dto);
                    return dto;
                })
                .collect(Collectors.toList());
        return recommendations;
    }

    /**
     * 초기 매칭 점수를 계산합니다.
     * 사용자가 선호하는 스타일과 상품 설명의 키워드 일치 정도를 점수로 평가합니다.
     *
     * @param userPreference 사용자 선호 정보
     * @param productKeywords 상품 설명에서 추출한 키워드 리스트
     * @return 초기 매칭 점수
     */
    private double calculateInitialMatchingScore(Preference userPreference, List<String> productKeywords) {
        double score = 0.0;
        if (userPreference.getStyle() != null && !userPreference.getStyle().isEmpty()) {
            for (String keyword : productKeywords) {
                if (keyword.toLowerCase().contains(userPreference.getStyle().toLowerCase())) {
                    score += 1.0;
                }
            }
        }
        // 필요에 따라 color, size 등 다른 선호 항목에 대한 점수 계산 로직 추가 가능
        return score;
    }

    /**
     * ChatGPT를 사용하여 상품 설명과 사용자 선호 간의 정밀 매칭 점수를 비동기적으로 산출합니다.
     * 프롬프트에는 사용자 선호 스타일과 상품 설명을 전달하여,
     * ChatGPT가 0에서 1 사이의 점수를 반환하도록 요청합니다.
     *
     * @param userPreference 사용자 선호 정보
     * @param product 평가할 상품 객체
     * @return 정밀 매칭 점수를 나타내는 CompletableFuture
     */
    private CompletableFuture<Double> deepMatchingScoreAsync(Preference userPreference, Product product) {
        String prompt = "사용자 선호: " + userPreference.getStyle() + "\n"
                + "상품 설명: " + product.getDescription() + "\n"
                + "이 상품이 사용자의 선호 스타일과 얼마나 잘 일치하는지 0에서 1 사이의 점수로 평가해줘. "
                + "그리고 간략하게 이유를 한 문장으로 설명해줘.";
        return chatGPTIntegrationService.extractUserPreferenceFromChatGPTAsync(Collections.emptyList(), prompt)
                .thenApply(response -> {
                    try {
                        return Double.parseDouble(response.trim());
                    } catch (Exception e) {
                        log.error("정밀 매칭 점수 파싱 실패: {}", e.getMessage());
                        return 0.0;
                    }
                });
    }
}
