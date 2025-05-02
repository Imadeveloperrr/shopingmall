package com.example.crud.ai.embedding.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class EmbeddingConfig {

    /**
     *  embedding-client에서 주입받을 CircuitBreaker 빈
     */
    @Bean
    public CircuitBreaker embeddingCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                // 실패율 50% 넘으면 오픈
                .slowCallRateThreshold(50)               // 느린 호출이 50% 넘으면 오픈
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // 2초 이상은 느린 호출
                .minimumNumberOfCalls(10)                // 최소 10번 호출 후에 통계 반영
                .waitDurationInOpenState(Duration.ofSeconds(30)) // 오픈 상태 유휴 시간
                .permittedNumberOfCallsInHalfOpenState(5)        // half-open 상태에서 허용 호출 수
                .build();

        return CircuitBreaker.of("embeddingService", config);
    }
}
