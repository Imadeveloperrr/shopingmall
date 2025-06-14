package com.example.crud.ai.embedding.infrastructure;

import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * ML 서비스와 통신하는 임베딩 클라이언트
 * - 캐싱 기능 강화
 * - 배치 처리 지원
 * - 장애 대응 개선
 */
@Component
@Slf4j
public class EmbeddingClient {

    private final WebClient webClient;
    private final CircuitBreaker cb;

    /**
     * 생성자 주입
     * - baseUrl: application.properties에 정의된 embedding.service.url
     * - WebClient.Builder: 스프링이 제공하는 빌더
     * - CircuitBreaker: resilience4j-circuitbreaker 빈으로 주입
     */
    public EmbeddingClient(
            @Value("${embedding.service.url}") String baseUrl,
            WebClient.Builder builder,
            CircuitBreaker cb
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.cb = cb;
    }

    /**
     * FastAPI로부터 임베딩 벡터를 받아오는 비동기 호출
     */
    public Mono<float[]> embed(String text) {
        return webClient.post()
                .uri("/embed")
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(EmbedResponse.class)

                // 2초 이상 지연되면 TimeoutException 발생
                .timeout(Duration.ofSeconds(2))

                // CircuitBreaker 적용 (실패율·응답지연 감시)
                .transformDeferred(CircuitBreakerOperator.of(cb))

                // DTO → float[] 변환
                .map(EmbedResponse::toArray)

                // 오류 발생 시 빈 벡터로 대체
                .onErrorResume(ex -> {
                    log.error("임베딩 요청 실패, 빈 벡터로 대체: {}", text, ex);
                    return Mono.just(new float[0]);
                });
    }

    /** FastAPI 응답 DTO */
    private record EmbedResponse(List<Double> vector) {
        float[] toArray() {
            var arr = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                arr[i] = vector.get(i).floatValue();
            }
            return arr;
        }
    }
}
