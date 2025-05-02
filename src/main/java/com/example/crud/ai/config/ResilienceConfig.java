package com.example.crud.ai.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(ChatGptProperties p) {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(40)
                .slowCallRateThreshold(40)
                .slowCallDurationThreshold(Duration.ofSeconds(4))
                .minimumNumberOfCalls(20)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .build();
        return CircuitBreakerRegistry.of(cbConfig);
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry(ChatGptProperties p) {
        RateLimiterConfig rlConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(p.rateLimitPerSec())
                .timeoutDuration(Duration.ofMillis(500))
                .build();
        return RateLimiterRegistry.of(rlConfig);
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry(ChatGptProperties p) {
        TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(p.timeoutSec()))
                .build();
        return TimeLimiterRegistry.of(tlConfig);
    }

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        BulkheadConfig bhConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ofSeconds(1))
                .build();
        return BulkheadRegistry.of(bhConfig);
    }
}

