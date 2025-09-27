package com.example.crud.ai.embedding.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 상품 설명 정제 서비스
 * 상품 설명에서 다른 상품 언급 및 노이즈를 제거하여 정확한 임베딩을 위한 텍스트로 변환
 */
@Service
@Slf4j
public class DescriptionRefinementService {

    private final WebClient webClient;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public DescriptionRefinementService(@Qualifier("embeddingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 상품 설명을 정제하여 임베딩에 적합한 형태로 변환
     */
    public String refineProductDescription(String originalDescription) {
        if (originalDescription == null || originalDescription.trim().isEmpty()) {
            return originalDescription;
        }

        try {
            // GPT로 설명 정제
            String refinedDescription = callGPTForRefinement(originalDescription);

            log.debug("상품 설명 정제 완료: 원본 길이={}, 정제 길이={}",
                     originalDescription.length(), refinedDescription.length());

            return refinedDescription;

        } catch (Exception e) {
            log.error("상품 설명 정제 실패, 원본 사용: {}", e.getMessage(), e);
            // 정제 실패 시 원본 반환
            return originalDescription;
        }
    }

    /**
     * GPT API를 호출하여 상품 설명 정제
     */
    private String callGPTForRefinement(String description) {
        String prompt = String.format("""
            이 상품 설명에서 다른 상품과의 조합이나 코디 제안 부분만 제거해주세요.

            제거해야 할 내용:
            - "~와 함께", "~와 매치", "~와 어울림" 등으로 다른 상품과의 조합을 설명하는 부분
            - "셔츠와 함께", "바지와 매치", "코트 안에" 등 구체적인 다른 상품과의 스타일링 제안
            - 레이어드나 코디 제안에서 다른 상품을 언급하는 부분

            반드시 보존해야 할 내용:
            - 이 상품 자체의 카테고리명 (니트, 셔츠, 바지 등)
            - 이 상품만의 소재, 핏, 실루엣, 디자인, 색상, 사이즈감 등
            - 착용감, 품질, 제작 방식 등 상품 자체 특성
            - 상품의 고유한 특징과 장점

            상품 설명:
            "%s"

            정제된 설명을 반환해주세요.
            """, description);

        Map<String, Object> request = Map.of(
            "model", "gpt-4o-mini",
            "messages", List.of(
                Map.of("role", "system", "content", "당신은 상품 설명 정제 전문가입니다. 상품 자체의 특성은 보존하면서 다른 상품 언급만 제거해주세요."),
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", 0.1,  // 일관성을 위해 낮은 temperature 사용
            "max_tokens", 1000
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block(); // non-block 개선필요.

            return parseGPTResponse(response);

        } catch (Exception e) {
            log.error("GPT API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("상품 설명 정제 중 API 호출 실패", e);
        }
    }

    /**
     * GPT 응답에서 정제된 설명 추출
     */
    @SuppressWarnings("unchecked")
    private String parseGPTResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("GPT 응답에 choices가 없음");
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            if (content == null || content.trim().isEmpty()) {
                throw new RuntimeException("GPT 응답 내용이 비어있음");
            }

            return content.trim();

        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패", e);
            throw new RuntimeException("GPT 응답 파싱 중 오류 발생", e);
        }
    }
}