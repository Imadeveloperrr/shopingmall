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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ML 서비스와 통신하는 임베딩 클라이언트 (개선된 버전)
 *
 * 주요 변경사항:
 * - Java 레벨 캐싱 제거 (Python ML 서비스에서만 캐싱)
 * - cache_key 생성 로직 제거 (Python이 알아서 처리)
 * - 단순화된 인터페이스
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
     * cache_key 전달 없이 Python이 알아서 캐시 키 생성
     */
    public Mono<float[]> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new float[0]);
        }

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);

            // cache_key 없이 텍스트만 전달
            Map<String, Object> request = Map.of("text", text.trim());

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
     * cache_keys 없이 Python이 알아서 처리
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

            // 텍스트 정리
            List<String> cleanedTexts = texts.stream()
                    .filter(t -> t != null && !t.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toList());

            // cache_keys 없이 텍스트만 전달
            Map<String, Object> request = Map.of("texts", cleanedTexts);

            return webClient.post()
                    .uri("/batch-embed")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(BatchEmbedResponse.class)
                    .timeout(DEFAULT_TIMEOUT.multipliedBy(2))
                    .map(response -> {
                        response.vectors().forEach(this::validateVector);
                        log.debug("배치 임베딩 완료: size={}, cached={}",
                                response.vectors().size(), response.cachedCount());
                        return response.vectors();
                    })
                    .doOnSuccess(v -> {
                        requestCounter.increment();
                        sample.stop(responseTimer);
                    })
                    .doOnError(this::handleError)
                    .transform(CircuitBreakerOperator.of(circuitBreaker))
                    .transform(BulkheadOperator.of(bulkhead))
                    .retryWhen(createRetrySpec())
                    .onErrorResume(e -> handleBatchFallback(texts));
        });
    }

    /**
     * 대량 배치를 청크로 나누어 처리
     */
    private Mono<List<float[]>> processBatchInChunks(List<String> texts) {
        return Mono.create(sink -> {
            List<float[]> allVectors = new ArrayList<>();
            List<Mono<List<float[]>>> chunkMonos = new ArrayList<>();

            for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
                int end = Math.min(i + MAX_BATCH_SIZE, texts.size());
                List<String> chunk = texts.subList(i, end);
                chunkMonos.add(batchEmbed(chunk));
            }

            Mono.zip(chunkMonos, results -> {
                        for (Object result : results) {
                            allVectors.addAll((List<float[]>) result);
                        }
                        return allVectors;
                    })
                    .subscribe(
                            sink::success,
                            sink::error
                    );
        });
    }

    /**
     * 벡터 차원 검증
     */
    private void validateVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            throw new IllegalArgumentException(
                    "잘못된 벡터 차원: expected=" + VECTOR_DIMENSION +
                            ", actual=" + (vector != null ? vector.length : "null")
            );
        }
    }

    /**
     * 캐시 히트/미스 메트릭 기록
     */
    private void recordCacheMetric(boolean cached) {
        Counter cacheCounter = Counter.builder("embedding.cache")
                .tag("result", cached ? "hit" : "miss")
                .description("Cache hit/miss count")
                .register(meterRegistry);
        cacheCounter.increment();
    }

    /**
     * 에러 처리
     */
    private void handleError(Throwable error) {
        errorCounter.increment();
        log.error("임베딩 요청 실패: {}", error.getMessage());
    }

    /**
     * 재시도 정책
     */
    private Retry createRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(1))
                .maxBackoff(Duration.ofSeconds(5))
                .filter(throwable -> !(throwable instanceof IllegalArgumentException))
                .doBeforeRetry(signal ->
                        log.warn("재시도 {}/{}: {}",
                                signal.totalRetries() + 1, 3,
                                signal.failure().getMessage())
                );
    }

    /**
     * 단일 임베딩 폴백
     */
    private Mono<float[]> handleFallback(Throwable error) {
        log.error("임베딩 생성 최종 실패: {}", error.getMessage());
        // 0 벡터 반환 (추천 시스템이 다른 방법으로 처리하도록)
        return Mono.just(new float[VECTOR_DIMENSION]);
    }

    /**
     * 배치 임베딩 폴백
     */
    private Mono<List<float[]>> handleBatchFallback(List<String> texts) {
        log.error("배치 임베딩 생성 최종 실패, 개별 처리 시도");
        // 개별 처리 시도 (더 느리지만 일부라도 성공 가능)
        List<Mono<float[]>> individualRequests = texts.stream()
                .map(this::embed)
                .collect(Collectors.toList());

        return Mono.zip(individualRequests, results ->
                Arrays.stream(results)
                        .map(r -> (float[]) r)
                        .collect(Collectors.toList())
        );
    }

    // DTO 클래스들
    private record EmbedResponse(
            float[] vector,
            boolean cached,
            int dimension,
            Float processingTimeMs
    ) {}

    private record BatchEmbedResponse(
            List<float[]> vectors,
            int cachedCount,
            int dimension,
            int batchSize,
            Float processingTimeMs
    ) {}
}