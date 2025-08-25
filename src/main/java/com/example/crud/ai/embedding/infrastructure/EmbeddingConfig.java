package com.example.crud.ai.embedding.infrastructure;

import org.springframework.context.annotation.Configuration;

/**
 * Embedding 관련 설정
 * CircuitBreaker는 ResilienceConfig에서 통합 관리
 */
@Configuration
public class EmbeddingConfig {
    // Circuit Breaker는 ResilienceConfig.embeddingCircuitBreaker()에서 정의됨
}
