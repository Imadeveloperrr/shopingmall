package com.example.crud.data.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ProductDescriptionAnalyzer {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 외부 키워드 추출 API URL과 API 키 (설정값이 없으면 로컬 기본 처리)
    @Value("${hf.keywords.api.url:}")
    private String keywordsApiUrl;

    @Value("${hf.api.key:}")
    private String hfApiKey;

    public ProductDescriptionAnalyzer(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 상품 설명에서 키워드를 추출합니다.
     * 외부 API가 설정되어 있으면 API 호출을 통해 키워드를 추출하고,
     * 그렇지 않으면 기본 로직으로 공백 기준 단순 분리한 단어들을 반환합니다.
     *
     * @param description 상품 설명 텍스트
     * @return 추출된 키워드 리스트
     */
    public List<String> extractKeywords(String description) {
        if (description == null || description.isEmpty()) {
            return Collections.emptyList();
        }

        // 외부 API URL이 설정되어 있는 경우
        if (keywordsApiUrl != null && !keywordsApiUrl.isEmpty()) {
            try {
                String response = webClient.post()
                        .uri(keywordsApiUrl)
                        .header("Authorization", "Bearer " + hfApiKey)
                        .bodyValue(Map.of("text", description))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(5))
                        .block();

                if (response != null && !response.isEmpty()) {
                    // 예시: 응답 JSON이 {"keywords": ["키워드1", "키워드2", ...]} 형태라고 가정
                    try {
                        Map<String, List<String>> result = objectMapper.readValue(response, Map.class);
                        List<String> keywords = result.get("keywords");
                        if (keywords != null && !keywords.isEmpty()) {
                            return keywords;
                        } else {
                            log.warn("응답 JSON에 'keywords' 필드가 비어있습니다. 원본 응답: {}", response);
                        }
                    } catch (Exception jsonEx) {
                        log.error("응답 JSON 파싱 실패: {}. 원본 응답: {}", jsonEx.getMessage(), response);
                        // JSON 파싱 실패 시 쉼표로 구분된 문자열로 간주
                        return Arrays.asList(response.split(","));
                    }
                } else {
                    log.warn("외부 키워드 추출 API로부터 빈 응답을 받았습니다.");
                }
            } catch (Exception e) {
                log.error("허깅페이스 키워드 추출 API 호출 실패: {}", e.getMessage());
            }
        }

        // 기본 로직: 공백을 기준으로 단순 분리 (실제 업무에서는 불용어 제거 등 추가 처리 필요)
        return Arrays.asList(description.split("\\s+"));
    }
}
