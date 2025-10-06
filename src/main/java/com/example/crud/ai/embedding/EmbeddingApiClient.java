package com.example.crud.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@Slf4j
public class EmbeddingApiClient {
    private final WebClient webClient;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public EmbeddingApiClient(@Qualifier("embeddingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @Async("embeddingTaskExecutor")
    @Cacheable(value = "embeddings", key = "#text.trim().toLowerCase().hashCode()")
    public CompletableFuture<float[]> generateEmbeddingAsync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new NullPointerException("임베딩 생성할 텍스트가 없습니다"));
        }

        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("OpenAI API 키가 설정되지 않았습니다"));
        }

        try {
            Map<String, Object> request = Map.of(
                    "input", text,
                    "model", "text-embedding-3-small"
            );

            return webClient.post()
                    .uri("https://api.openai.com/v1/embeddings")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(10))
                    .map(this::parseEmbeddingResponse)
                    .toFuture();
        } catch (Exception e) {
            log.error("임베딩 요청 실패", e);
            return CompletableFuture.failedFuture(new EmbeddingServiceException("임베딩 서비스를 일시적으로 사용할 수 없습니다"));
        }
    }

    private float[] parseEmbeddingResponse(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new RuntimeException("OpenAI API 응답에서 데이터를 찾을 수 없습니다");
        }

        Object firstItem = dataList.get(0);
        if (!(firstItem instanceof Map<?, ?> firstMap)) {
            throw new RuntimeException("OpenAI API 응답의 데이터 형식이 올바르지 않습니다");
        }

        Object embeddingObj = firstMap.get("embedding");
        if (!(embeddingObj instanceof List<?> rawList)) {
            throw new RuntimeException("OpenAI API 응답에 임베딩이 없습니다");
        }

        float[] result = new float[rawList.size()];
        for (int i = 0; i < rawList.size(); i++) {
            Object value = rawList.get(i);
            if (value instanceof Number number) {
                result[i] = number.floatValue();
            } else {
                throw new RuntimeException("임베딩 벡터 요소가 숫자가 아닙니다: " + value);
            }
        }

        log.debug("임베딩 차원: {}", result.length);
        return result;
    }
}