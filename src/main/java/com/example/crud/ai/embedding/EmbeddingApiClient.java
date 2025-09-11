package com.example.crud.ai.embedding;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 임베딩 API 클라이언트 - OpenAI API를 활용한 벡터 임베딩 생성
 * - 1536차원 벡터 생성 (text-embedding-3-small 모델)
 * - 인메모리 캐싱으로 성능 최적화
 * - 배치 처리 지원 (병렬 처리)
 * - 코사인 유사도 계산 기능
 */
@Service
@Slf4j
public class EmbeddingApiClient {
    private final WebClient webClient;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public EmbeddingApiClient(@Qualifier("embeddingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) { // 널값 체크.
            throw new NullPointerException("임베딩 생성할 텍스트가 없습니다.");
        }

        String cacheKey = "emb:" + text.trim().toLowerCase().hashCode();
        if (embeddingCache.containsKey(cacheKey)) {
            return embeddingCache.get(cacheKey);
        }

        try {
            float[] embedding;
            String method;

            if (openaiApiKey != null && !openaiApiKey.trim().isEmpty()) {
                embedding = generateOpenAIEmbedding(text);
                method = "OpenAI";
            } else {
                throw new RuntimeException("OpenAI API키가 설정되지 않았습니다.");
            }

            if (embeddingCache.size() < 1000) {
                embeddingCache.put(cacheKey, embedding);
            }

            log.debug("임베딩 생성 완료: method={}", method);
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
                .timeout(Duration.ofSeconds(3))
                .block(); // 나중에 비동기 처리로 전부다 업데이트.

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<Double> embedding = (List<Double>) data.get(0).get("embedding");

        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
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