package com.example.crud.ai.embedding.infrastructure;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ML 서비스와 통신하는 임베딩 클라이언트 (개선된 버전)
 *
 * 주요 개선사항:
 * - Redis 캐싱 제거 (ML 서비스에서 처리)
 * - Bulkhead 패턴 추가
 * - 메트릭 수집 강화
 * - 배치 처리 최적화
 * - 에러 처리 개선
 */
@Component
@Slf4j
public class EmbeddingClient {

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final MeterRegistry meterRegistry;

    // 상수
    private static final int VECTOR_DIMENSION = 384;
    private static final int MAX_BATCH_SIZE = 50;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    // 메트릭
    private final Counter requestCounter;
    private final Counter errorCounter;
    private final Timer responseTimer;

    public EmbeddingClient(
            @Value("${embedding.service.url}") String baseUrl,
            @Value("${embedding.service.timeout:5000}") int timeoutMs,
            WebClient.Builder builder,
            CircuitBreaker embeddingCircuitBreaker,
            Bulkhead embeddingBulkhead,
            MeterRegistry meterRegistry
    ) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();

        this.circuitBreaker = embeddingCircuitBreaker;
        this.bulkhead = embeddingBulkhead;
        this.meterRegistry = meterRegistry;

        // 메트릭 초기화
        this.requestCounter = Counter.builder("embedding.requests")
                .description("Total embedding requests")
                .register(meterRegistry);

        this.errorCounter = Counter.builder("embedding.errors")
                .description("Total embedding errors")
                .register(meterRegistry);

        this.responseTimer = Timer.builder("embedding.response.time")
                .description("Embedding response time")
                .register(meterRegistry);
    }

    /**
     * 단일 텍스트 임베딩 생성
     */
    public Mono<float[]> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new float[0]);
        }

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            Map<String, Object> request = Map.of(
                    "text", text.trim(),
                    "cache_key", generateCacheKey(text)
            );

            return webClient.post()
                    .uri("/embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(EmbedResponse.class)
                    .timeout(DEFAULT_TIMEOUT)
                    .map(response -> {
                        validateVector(response.vector());
                        recordCacheMetric(response.cached());
                        return response.vector();
                    })
                    .doOnSuccess(v -> {
                        requestCounter.increment();
                        sample.stop(responseTimer);
                    })
                    .doOnError(this::handleError)
                    .transform(CircuitBreakerOperator.of(circuitBreaker))
                    .transform(BulkheadOperator.of(bulkhead))
                    .retryWhen(createRetrySpec())
                    .onErrorResume(this::handleFallback);
        });
    }

    /**
     * 배치 임베딩 생성
     */
    public Mono<List<float[]>> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(List.of());
        }

        // 단일 텍스트는 단일 API 사용
        if (texts.size() == 1) {
            return embed(texts.get(0)).map(List::of);
        }

        // 대량 배치는 청크로 분할
        if (texts.size() > MAX_BATCH_SIZE) {
            return processBatchInChunks(texts);
        }

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            // 텍스트 정리 및 캐시 키 생성
            List<String> cleanedTexts = texts.stream()
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            List<String> cacheKeys = cleanedTexts.stream()
                    .map(this::generateCacheKey)
                    .collect(Collectors.toList());

            Map<String, Object> request = Map.of(
                    "texts", cleanedTexts,
                    "cache_keys", cacheKeys
            );

            return webClient.post()
                    .uri("/batch-embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BatchEmbedResponse.class)
                    .timeout(Duration.ofSeconds(10)) // 배치는 더 긴 타임아웃
                    .map(response -> {
                        List<float[]> vectors = response.vectors().stream()
                                .map(this::convertToFloatArray)
                                .collect(Collectors.toList());

                        recordBatchMetrics(response);
                        return vectors;
                    })
                    .doOnSuccess(v -> {
                        requestCounter.increment();
                        sample.stop(responseTimer);
                    })
                    .doOnError(this::handleError)
                    .transform(CircuitBreakerOperator.of(circuitBreaker))
                    .transform(BulkheadOperator.of(bulkhead))
                    .retryWhen(createRetrySpec())
                    .onErrorResume(e -> handleBatchFallback(texts, e));
        });
    }

    /**
     * ML 서비스 헬스 체크
     */
    public Mono<Boolean> checkHealth() {
        return webClient.get()
                .uri("/healthz")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .map(health -> "healthy".equals(health.status()))
                .timeout(Duration.ofSeconds(2))
                .doOnError(e -> log.error("ML 서비스 헬스 체크 실패", e))
                .onErrorReturn(false);
    }

    /**
     * 서비스 통계 조회
     */
    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> getStats() {
        return webClient.get()
                .uri("/stats")
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(new HashMap<>());
    }

    /**
     * 대량 배치를 청크로 나누어 처리
     */
    private Mono<List<float[]>> processBatchInChunks(List<String> texts) {
        List<List<String>> chunks = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            chunks.add(texts.subList(i, Math.min(texts.size(), i + MAX_BATCH_SIZE)));
        }

        return Mono.zip(
                chunks.stream()
                        .map(this::batchEmbed)
                        .collect(Collectors.toList()),
                results -> {
                    List<float[]> combined = new ArrayList<>();
                    for (Object result : results) {
                        combined.addAll((List<float[]>) result);
                    }
                    return combined;
                }
        );
    }

    /**
     * 재시도 정책 생성
     */
    private Retry createRetrySpec() {
        return Retry.backoff(3, Duration.ofMillis(100))
                .maxBackoff(Duration.ofSeconds(1))
                .filter(throwable -> {
                    if (throwable instanceof WebClientResponseException) {
                        int status = ((WebClientResponseException) throwable).getStatusCode().value();
                        // 4xx 에러는 재시도하지 않음
                        return status >= 500 || status == 429;
                    }
                    return true;
                })
                .doBeforeRetry(signal ->
                        log.warn("재시도 시도: attempt={}, error={}",
                                signal.totalRetries() + 1,
                                signal.failure().getMessage())
                );
    }

    /**
     * 캐시 키 생성
     */
    private String generateCacheKey(String text) {
        return "embed:" + text.hashCode();
    }

    /**
     * 벡터 검증
     */
    private void validateVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            throw new IllegalStateException(
                    String.format("잘못된 벡터 차원: expected=%d, actual=%d",
                            VECTOR_DIMENSION,
                            vector != null ? vector.length : 0)
            );
        }
    }

    /**
     * List<Double>을 float[]로 변환
     */
    private float[] convertToFloatArray(List<Double> list) {
        if (list == null) {
            return new float[0];
        }

        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i).floatValue();
        }

        validateVector(array);
        return array;
    }

    /**
     * 에러 처리
     */
    private void handleError(Throwable error) {
        errorCounter.increment();

        if (error instanceof WebClientResponseException) {
            WebClientResponseException wcError = (WebClientResponseException) error;
            log.error("ML 서비스 응답 에러: status={}, body={}",
                    wcError.getStatusCode(),
                    wcError.getResponseBodyAsString());
        } else {
            log.error("ML 서비스 호출 실패", error);
        }
    }

    /**
     * 단일 요청 폴백
     */
    private Mono<float[]> handleFallback(Throwable error) {
        log.warn("임베딩 생성 실패, 빈 벡터 반환: {}", error.getMessage());

        // 메트릭 기록
        meterRegistry.counter("embedding.fallback").increment();

        // 빈 벡터 반환 (차원은 맞춤)
        return Mono.just(new float[VECTOR_DIMENSION]);
    }

    /**
     * 배치 요청 폴백
     */
    private Mono<List<float[]>> handleBatchFallback(List<String> texts, Throwable error) {
        log.warn("배치 임베딩 실패, 개별 처리 시도: {}", error.getMessage());

        // 개별 처리로 폴백
        List<Mono<float[]>> individualRequests = texts.stream()
                .map(this::embed)
                .collect(Collectors.toList());

        return Mono.zip(individualRequests, arrays -> {
            List<float[]> results = new ArrayList<>();
            for (Object array : arrays) {
                results.add((float[]) array);
            }
            return results;
        });
    }

    /**
     * 캐시 메트릭 기록
     */
    private void recordCacheMetric(boolean cached) {
        meterRegistry.counter("embedding.cache", "hit", String.valueOf(cached))
                .increment();
    }

    /**
     * 배치 메트릭 기록
     */
    private void recordBatchMetrics(BatchEmbedResponse response) {
        meterRegistry.gauge("embedding.batch.size", response.batchSize());
        meterRegistry.gauge("embedding.batch.cached", response.cachedCount());

        if (response.processingTimeMs() != null) {
            meterRegistry.timer("embedding.batch.processing")
                    .record(response.processingTimeMs().longValue(), TimeUnit.MILLISECONDS);
        }
    }

    // DTO 클래스들
    private record EmbedResponse(
            float[] vector,
            boolean cached,
            int dimension
    ) {}

    private record BatchEmbedResponse(
            List<List<Double>> vectors,
            int cachedCount,
            int dimension,
            Float processingTimeMs,
            int batchSize
    ) {}

    private record HealthResponse(
            String status,
            boolean modelLoaded,
            boolean cacheHealthy,
            int dimension,
            String modelName
    ) {}
}