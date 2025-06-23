package com.example.crud.ai.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 통합 설정
 *
 * 각 외부 서비스별로 적절한 장애 대응 패턴 적용
 */
@Configuration
@Slf4j
public class ResilienceConfig {

    // ==================== ChatGPT 서비스 ====================

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ChatGptProperties p) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(40)               // 실패율 40% 이상 시 오픈
                .slowCallRateThreshold(40)              // 느린 호출 40% 이상 시 오픈
                .slowCallDurationThreshold(Duration.ofSeconds(4))  // 4초 이상이면 느린 호출
                .minimumNumberOfCalls(20)               // 최소 20번 호출 후 통계 계산
                .waitDurationInOpenState(Duration.ofSeconds(20))   // 오픈 상태 유지 시간
                .permittedNumberOfCallsInHalfOpenState(5)         // Half-open 상태에서 테스트 호출 수
                .slidingWindowSize(100)                 // 슬라이딩 윈도우 크기
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();

        return CircuitBreakerRegistry.of(cbConfig);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(ChatGptProperties p) {
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))        // 1초마다 리프레시
                .limitForPeriod(p.rateLimitPerSec())              // 초당 허용 요청 수
                .timeoutDuration(Duration.ofMillis(500))          // 대기 타임아웃
                .build();

        return RateLimiterRegistry.of(rlConfig);
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry(ChatGptProperties p) {
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(p.timeoutSec()))
                .cancelRunningFuture(true)              // 타임아웃 시 실행 중인 작업 취소
                .build();

        return TimeLimiterRegistry.of(tlConfig);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig bhConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(20)                 // 최대 동시 호출 수
                .maxWaitDuration(Duration.ofSeconds(1)) // 최대 대기 시간
                .writableStackTraceEnabled(false)       // 성능을 위해 스택 트레이스 비활성화
                .build();

        return BulkheadRegistry.of(bhConfig);
    }

    // ==================== ML 임베딩 서비스 ====================

    /**
     * ML 서비스 전용 Circuit Breaker
     * - 더 관대한 설정 (ML 서비스는 가끔 느릴 수 있음)
     */
    @Bean
    public CircuitBreaker embeddingCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)               // 실패율 50% 이상 시 오픈
                .slowCallRateThreshold(50)              // 느린 호출 50% 이상 시 오픈
                .slowCallDurationThreshold(Duration.ofSeconds(2))  // 2초 이상이면 느린 호출
                .minimumNumberOfCalls(10)               // 최소 10번 호출 후 통계
                .waitDurationInOpenState(Duration.ofSeconds(30))   // 30초 대기
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .ignoreExceptions(IllegalArgumentException.class)  // 잘못된 입력은 무시
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("embeddingService", config);

        // 이벤트 리스너 추가 (모니터링용)
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.warn("ML 서비스 Circuit Breaker 상태 변경: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState())
                )
                .onFailureRateExceeded(event ->
                        log.error("ML 서비스 실패율 초과: {}%", event.getFailureRate())
                );

        return circuitBreaker;
    }

    /**
     * ML 서비스 전용 Bulkhead
     * - 동시 호출 제한으로 서비스 보호
     */
    @Bean
    public Bulkhead embeddingBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(10)                 // ML 서비스는 동시 10개까지
                .maxWaitDuration(Duration.ofSeconds(1)) // 1초까지 대기
                .writableStackTraceEnabled(false)
                .build();

        Bulkhead bulkhead = Bulkhead.of("embeddingService", config);

        // 이벤트 리스너
        bulkhead.getEventPublisher()
                .onCallRejected(event ->
                        log.warn("ML 서비스 호출 거부됨 (Bulkhead 가득참)")
                );

        return bulkhead;
    }

    // ==================== Kafka 프로듀서 ====================

    /**
     * Kafka 프로듀서용 Circuit Breaker
     * - 메시지 발행 실패에 대한 보호
     */
    @Bean
    public CircuitBreaker kafkaProducerCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)               // Kafka는 좀 더 관대하게
                .minimumNumberOfCalls(5)                // 최소 호출 수도 낮게
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .slidingWindowSize(20)
                .build();

        return CircuitBreaker.of("kafkaProducer", config);
    }

    // ==================== Redis 캐시 ====================

    /**
     * Redis 캐시용 Circuit Breaker
     * - 캐시 장애가 전체 서비스에 영향을 주지 않도록
     */
    @Bean
    public CircuitBreaker redisCacheCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(70)               // 캐시는 실패해도 큰 문제 없음
                .slowCallRateThreshold(70)
                .slowCallDurationThreshold(Duration.ofMillis(500))  // 500ms
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(5))     // 빠른 복구 시도
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(30)
                .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("redisCache", config);

        // 캐시 장애 시 로깅만 (서비스는 계속 동작)
        circuitBreaker.getEventPublisher()
                .onStateTransition(event ->
                        log.info("Redis 캐시 Circuit Breaker 상태: {} -> {}",
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState())
                );

        return circuitBreaker;
    }

    // ==================== Elasticsearch ====================

    /**
     * Elasticsearch용 Circuit Breaker
     */
    @Bean
    public CircuitBreaker elasticsearchCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(40)
                .build();

        return CircuitBreaker.of("elasticsearch", config);
    }

    // ==================== 공통 설정 ====================

    /**
     * 기본 Retry 설정 (필요 시 사용)
     */
//    @Bean
//    public io.github.resilience4j.retry.RetryConfig defaultRetryConfig() {
//        return io.github.resilience4j.retry.RetryConfig.custom()
//                .maxAttempts(3)
//                .waitDuration(Duration.ofMillis(500))
//                .retryExceptions(Exception.class)
//                .ignoreExceptions(IllegalArgumentException.class)
//                .build();
//    }
}