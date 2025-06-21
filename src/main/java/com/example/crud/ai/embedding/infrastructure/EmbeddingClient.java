package com.example.crud.ai.embedding.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ML 서비스와 통신하는 임베딩 클라이언트
 * - 캐싱 기능 내장
 * - Circuit Breaker로 장애 대응
 * - 재시도 로직 포함
 */
@Component
@Slf4j
public class EmbeddingClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "embedding:";
    private static final int VECTOR_DIMENSION = 384;
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    public EmbeddingClient(
            @Value("${embedding.service.url}") String baseUrl,
            @Value("${embedding.service.timeout:5000}") int timeout,
            WebClient.Builder builder,
            CircuitBreaker embeddingCircuitBreaker,
            RedisTemplate<String, Object> redisTemplate
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        this.circuitBreaker = embeddingCircuitBreaker;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 단일 텍스트 임베딩 생성
     *
     * @param text 임베딩할 텍스트
     * @return 임베딩 벡터 (384차원)
     */
    public Mono<float[]> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new float[0]);
        }

        // 캐시 키 생성
        String cacheKey = CACHE_PREFIX + text.hashCode();

        // 1. 캐시 확인
        return Mono.fromCallable(() -> {
                    Object cached = redisTemplate.opsForValue().get(cacheKey);
                    if (cached != null) {
                        log.debug("임베딩 캐시 히트: key={}", cacheKey);
                        if (cached instanceof float[]) {
                            return (float[]) cached;
                        } else if (cached instanceof List) {
                            // List를 float[]로 변환
                            List<Double> list = (List<Double>) cached;
                            float[] array = new float[list.size()];
                            for (int i = 0; i < list.size(); i++) {
                                array[i] = list.get(i).floatValue();
                            }
                            return array;
                        }
                    }
                    return null;
                })
                .switchIfEmpty(
                        // 2. 캐시 미스 시 ML 서비스 호출
                        callMLService(text)
                                .doOnNext(embedding -> {
                                    // 3. 결과 캐싱 (비동기)
                                    if (embedding != null && embedding.length > 0) {
                                        Mono.fromRunnable(() -> {
                                            try {
                                                redisTemplate.opsForValue().set(
                                                        cacheKey, embedding,
                                                        CACHE_TTL.toSeconds(), TimeUnit.SECONDS
                                                );
                                                log.debug("임베딩 캐시 저장: key={}", cacheKey);
                                            } catch (Exception e) {
                                                log.error("임베딩 캐싱 실패", e);
                                            }
                                        }).subscribe();
                                    }
                                })
                );
    }

    /**
     * 배치 임베딩 생성
     *
     * @param texts 임베딩할 텍스트 목록
     * @return 임베딩 벡터 목록
     */
    public Mono<List<float[]>> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }

        if (texts.size() == 1) {
            return embed(texts.get(0)).map(List::of);
        }

        // 배치 요청 생성
        Map<String, Object> request = Map.of(
                "texts", texts,
                "cache_keys", texts.stream()
                        .map(t -> CACHE_PREFIX + t.hashCode())
                        .toList()
        );

        return webClient.post()
                .uri("/batch-embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BatchEmbedResponse.class)
                .timeout(Duration.ofSeconds(10))
                .transform(this::applyResiliencePatterns)
                .map(response -> response.vectors())
                .doOnError(e -> log.error("배치 임베딩 요청 실패: size={}", texts.size(), e))
                .onErrorReturn(List.of());
    }

    /**
     * ML 서비스 헬스 체크
     *
     * @return 서비스 상태 (true: 정상, false: 비정상)
     */
    public Mono<Boolean> checkHealth() {
        return webClient.get()
                .uri("/healthz")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .map(health -> "healthy".equals(health.status()))
                .timeout(Duration.ofSeconds(1))
                .doOnError(e -> log.error("ML 서비스 헬스 체크 실패", e))
                .onErrorReturn(false);
    }

    /**
     * 실제 ML 서비스 호출
     */
    private Mono<float[]> callMLService(String text) {
        Map<String, Object> request = Map.of(
                "text", text,
                "cache_key", CACHE_PREFIX + text.hashCode()
        );

        return webClient.post()
                .uri("/embed")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .timeout(Duration.ofSeconds(3))
                .transform(this::applyResiliencePatterns)
                .map(response -> {
                    float[] vector = response.toArray();
                    if (vector.length != VECTOR_DIMENSION) {
                        log.warn("벡터 차원 불일치: expected={}, actual={}",
                                VECTOR_DIMENSION, vector.length);
                    }
                    return vector;
                })
                .doOnError(e -> {
                    if (e instanceof WebClientResponseException) {
                        WebClientResponseException wcre = (WebClientResponseException) e;
                        log.error("ML 서비스 응답 오류: status={}, body={}",
                                wcre.getStatusCode(), wcre.getResponseBodyAsString());
                    } else {
                        log.error("임베딩 생성 실패: text={}", text.substring(0, Math.min(50, text.length())), e);
                    }
                })
                .onErrorReturn(new float[0]);
    }

    /**
     * Resilience4j 패턴 적용
     * - Circuit Breaker: 연속 실패 시 차단
     * - Retry: 일시적 오류에 대한 재시도
     */
    private <T> Mono<T> applyResiliencePatterns(Mono<T> mono) {
        return mono
                .transform(CircuitBreakerOperator.of(circuitBreaker))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .filter(throwable -> {
                            // 재시도할 예외 판단
                            if (throwable instanceof WebClientResponseException) {
                                WebClientResponseException e = (WebClientResponseException) throwable;
                                // 5xx 에러만 재시도
                                return e.getStatusCode().is5xxServerError();
                            }
                            // 네트워크 오류 등은 재시도
                            return !(throwable instanceof IllegalArgumentException);
                        })
                        .doBeforeRetry(signal ->
                                log.debug("임베딩 요청 재시도: attempt={}", signal.totalRetries() + 1)
                        )
                );
    }

    // Response DTOs
    private record EmbedResponse(
            List<Double> vector,
            boolean cached,
            int dimension
    ) {
        float[] toArray() {
            if (vector == null || vector.isEmpty()) {
                return new float[0];
            }

            float[] arr = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                arr[i] = vector.get(i).floatValue();
            }
            return arr;
        }
    }

    private record BatchEmbedResponse(
            List<float[]> vectors,
            int cached_count,
            int dimension
    ) {}

    private record HealthResponse(
            String status,
            boolean model_loaded,
            boolean cache_healthy,
            int dimension,
            String model_name
    ) {}
}