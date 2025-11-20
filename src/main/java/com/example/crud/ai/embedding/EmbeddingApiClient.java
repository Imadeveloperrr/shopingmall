package com.example.crud.ai.embedding;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class EmbeddingApiClient {
    private final WebClient webClient;
    private final CacheManager cacheManager;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    public EmbeddingApiClient(@Qualifier("embeddingWebClient") WebClient webClient, CacheManager cacheManager) {
        this.webClient = webClient;
        this.cacheManager = cacheManager;
    }

    // @Cacheable(value = "embedding", key = "#text.trim().toLowerCase().hashCode()") @Async랑 쓰면 프록시충돌.
    //@Async("embeddingTaskExecutor")
    public CompletableFuture<float[]> generateEmbeddingAsync(String text) {
        log.info("=== generateEmbeddingAsync 시작: textLength={}", text != null ? text.length() : 0);
        if (text == null || text.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new BaseException(ErrorCode.INVALID_MESSAGE_INPUT, "임베딩 생성할 텍스트가 없습니다"));
        }

        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            log.error("OpenAI API 키가 설정되지 않았습니다");
            return CompletableFuture.failedFuture(new BaseException(ErrorCode.AI_SERVICE_UNAVAILABLE, "OpenAI API 키가 설정되지 않았습니다"));
        }

        Cache cache = cacheManager.getCache("embeddings");

        if (cache == null) {
            log.info("Redis System Error, 캐시 없이 진행");
        } else {
            String cacheKey = String.valueOf(text.trim().toLowerCase().hashCode());
            log.debug("Redis Cache Key Generate {}", cacheKey);

            Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null) {
                Object cacheValue = wrapper.get();
                if (cacheValue instanceof float[]) {
                    float[] cachedEmbedding = (float[]) cacheValue;
                    log.info("Redis Cache Hit Key {}", cacheKey);
                    return CompletableFuture.completedFuture(cachedEmbedding);
                } else {
                    log.warn("Redis Cache Wrong Type {}", cacheValue != null ? cacheValue.getClass() : "null");
                }
            } else {
                log.info("Redis Cache Miss {}", cacheKey);
            }
        }


        try {
            log.info("OpenAI API 호출 준비 중...");
            String normalized = text.trim().toLowerCase();
            Map<String, Object> request = Map.of(
                    "input", normalized, // Cache Key와 동일한 정규화 텍스트를 API요청.
                    "model", "text-embedding-3-small"
            );

            log.info("WebClient로 API 호출 시작");
            CompletableFuture<float[]> future = webClient.post()
                    .uri("/v1/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(10))
                    .map(this::parseEmbeddingResponse)
                    .toFuture();

            if (cache != null) {
                String cacheKey = String.valueOf(normalized.hashCode());

                future = future.thenApply(result -> {
                    cache.put(cacheKey, result);
                    log.info("Redis : Cache Success Save {}", cacheKey);
                    return result;
                });
            }

            return future;
        } catch (Exception e) {
            log.error("임베딩 요청 실패", e);
            return CompletableFuture.failedFuture(new BaseException(ErrorCode.AI_SERVICE_UNAVAILABLE, "임베딩 서비스를 일시적으로 사용할 수 없습니다"));
        }
    }

    private float[] parseEmbeddingResponse(Map<String, Object> response) {
        Object dataObj = response.get("data");
        if (!(dataObj instanceof List<?> dataList) || dataList.isEmpty()) {
            throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, "OpenAI API 응답에서 데이터를 찾을 수 없습니다");
        }

        Object firstItem = dataList.get(0);
        if (!(firstItem instanceof Map<?, ?> firstMap)) {
            throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, "OpenAI API 응답의 데이터 형식이 올바르지 않습니다");
        }

        Object embeddingObj = firstMap.get("embedding");
        if (!(embeddingObj instanceof List<?> rawList)) {
            throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, "OpenAI API 응답에 임베딩이 없습니다");
        }

        float[] result = new float[rawList.size()];
        for (int i = 0; i < rawList.size(); i++) {
            Object value = rawList.get(i);
            if (value instanceof Number number) {
                result[i] = number.floatValue();
            } else {
                throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, "임베딩 벡터 요소가 숫자가 아닙니다: " + value);
            }
        }

        log.debug("임베딩 차원: {}", result.length);
        return result;
    }
}
