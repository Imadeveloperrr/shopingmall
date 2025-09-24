package com.example.crud.ai.embedding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 임베딩 API 클라이언트 - OpenAI API를 활용한 벡터 임베딩 생성
 * - 1536차원 벡터 생성 (text-embedding-3-small 모델)
 * - Redis 분산 캐싱으로 성능 최적화
 * - 배치 처리 지원 (병렬 처리)
 * - 코사인 유사도 계산 기능
 */
@Service
@Slf4j
public class EmbeddingApiClient {
    private final WebClient webClient;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public EmbeddingApiClient(@Qualifier("embeddingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    // 임시로 캐시 비활성화 (Redis 연결 문제로 인해)
    // @Cacheable(value = "embeddings", key = "#text.trim().toLowerCase().hashCode()")
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new NullPointerException("임베딩 생성할 텍스트가 없습니다.");
        }

        try {
            if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
                throw new RuntimeException("OpenAI API키가 설정되지 않았습니다.");
            }

            float[] embedding = generateOpenAIEmbedding(text);
            log.debug("임베딩 생성 완료 - Redis 캐시 저장됨");
            return embedding;

        } catch (Exception e) {
            log.error("임베딩 생성 실패 {}", e.getMessage());
            throw new EmbeddingServiceException("서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private float[] generateOpenAIEmbedding(String text) {
        Map<String, Object> request = Map.of(
                "input", text,
                "model", "text-embedding-3-small"
        );

        Map<String, Object> response = webClient.post()
                .uri("https://api.openai.com/v1/embeddings")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(3)) // 병목지점
                .block(); // 나중에 비동기 처리로 전부다 업데이트. 병목지점

        // 안전한 타입 변환으로 ClassCastException 방지
        Object dataObj = response.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new RuntimeException("OpenAI API 응답에서 데이터를 찾을 수 없습니다.");
        }

        Object firstItem = dataList.get(0);
        if (!(firstItem instanceof Map<?, ?> firstMap)) {
            throw new RuntimeException("OpenAI API 응답의 데이터 형식이 올바르지 않습니다.");
        }

        Object embeddingObj = firstMap.get("embedding");

        if (!(embeddingObj instanceof List<?> rawList)) {
            throw new RuntimeException("OpenAI API 응답 형식이 올바르지 않습니다.");
        }

        float[] result = new float[rawList.size()];
        for (int i = 0; i < rawList.size(); i++) {
            if (rawList.get(i) instanceof Number number) {
                result[i] = number.floatValue();
            } else {
                throw new RuntimeException("임베딩 벡터 요소가 숫자가 아닙니다: " + rawList.get(i));
            }
        }
        return result;

    }


    public double calculateSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        // 소폭 개선: int 변수로 length 캐싱 (배열 접근 최소화)
        final int length = vector1.length;

        for (int i = 0; i < length; i++) {
            // 소폭 개선: 임시 변수로 중복 접근 방지
            final float v1 = vector1[i];
            final float v2 = vector2[i];

            dotProduct += v1 * v2;
            norm1 += v1 * v1;
            norm2 += v2 * v2;
        }

        // 기존과 동일
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

}