package com.example.crud.ai.embedding.infrastructure;

import com.example.crud.ai.recommendation.domain.dto.ChatMessage;
import com.example.crud.ai.recommendation.domain.dto.ChatPayload;
import com.example.crud.ai.config.ChatGptProperties;
import org.ehcache.Cache;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * ChatGptServiceLite 클래스
 *  - OpenAI의 ChatGPT API를 호출하고
 *    장애 대응(회로 차단기, 속도 제한, 시간 제한, 동시 호출 제한) 기능을 적용한 서비스입니다.
 *  - EHCache를 활용한 단기 L2 캐시도 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatGptServiceLite {

    // ------------------- 필드 주입 -------------------
    // OpenAI API 호출을 위한 WebClient
    private final WebClient chatGptClient;
    // 설정 정보(apiUrl, apiKey, timeout 등)가 담긴 프로퍼티 클래스
    private final ChatGptProperties prop;
    // Resilience4j 패턴별 레지스트리
    private final CircuitBreakerRegistry cbRegistry;    // 회로 차단기
    private final RateLimiterRegistry rlRegistry;        // 요청 속도 제한
    private final TimeLimiterRegistry tlRegistry;       // 호출 시간 제한
    private final BulkheadRegistry bhRegistry;          // 동시 호출 제한

    // ------------------- EHCache L2 캐시 설정 -------------------
    //  - 최대 10,000개 항목을 메모리에 저장
    //  - 항목별 TTL(Time To Live)을 30초로 설정
    private final Cache<String, String> l2 = CacheManagerBuilder.newCacheManagerBuilder()
            .withCache(
                    "chatgpt",  // 캐시 이름
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                    String.class, String.class,
                                    ResourcePoolsBuilder.heap(10_000)  // 최대 10,000개 항목
                            )
                            .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(
                                    Duration.ofSeconds(30)  // 30초 후 만료
                            ))
            )
            .build(true)
            .getCache("chatgpt", String.class, String.class);

    public Flux<String> stream(List<ChatMessage> history, String prompt) {
        // 1) 요청 페이로드 생성
        ChatPayload body = ChatPayload.build(history, prompt, prop);
        // 2) 캐시 키 생성: 요청 페이로드를 문자열로 변환 후 SHA-256 해시
        String key = DigestUtils.sha256Hex(body.toString());

        // 3) 캐시 조회
        String hit = l2.get(key);
        if (hit != null) {
            // 캐시 적중 시, 저장된 문자열을 하나의 Flux로 반환
            return Flux.just(hit);
        }

        // 4) OpenAI API 호출 (스트리밍 형식)
        return chatGptClient.post()
                .bodyValue(body.toMap(true))             // 스트리밍:true
                .retrieve()
                .bodyToFlux(String.class)                // 응답을 String Flux로 변환
                .transform(this::applyFluxGuards)         // 장애 대응 보호막 적용
                .doOnNext(chunk -> {
                    // 5) 응답 조각 중 일정 길이(chunkLimit) 이상만 캐시에 저장
                    if (chunk.length() > prop.streamChunkLimit()) {
                        l2.put(key, chunk);
                    }
                });
    }

    public Mono<String> completion(List<ChatMessage> history, String prompt) {
        // 1) 요청 페이로드 생성
        ChatPayload body = ChatPayload.build(history, prompt, prop);

        // 2) API 호출 및 장애 대응 보호막 적용
        return chatGptClient.post()
                .bodyValue(body.toMap(false))            // 스트리밍:false
                .retrieve()
                .bodyToMono(String.class)                // 응답을 String Mono로 변환
                .transform(this::applyMonoGuards);       // 보호막 적용
    }

    private <T> Flux<T> applyFluxGuards(Flux<T> flux) {
        // 보호막 인스턴스 가져오기 (이름: "chatgpt")
        var cb = cbRegistry.circuitBreaker("chatgpt");
        var rl = rlRegistry.rateLimiter("chatgpt");
        var tl = tlRegistry.timeLimiter("chatgpt");
        var bh = bhRegistry.bulkhead("chatgpt");

        return flux
                // 1) 회로 차단기: 실패율이 임계값 넘어가면 잠시 차단
                .transform(CircuitBreakerOperator.of(cb))
                // 2) 요청 속도 제한: 지정 초당 요청 수 초과 시 지연·예외
                .transform(RateLimiterOperator.of(rl))
                // 3) 시간 제한: 지정 시간 내 응답 없으면 예외
                .transform(TimeLimiterOperator.of(tl))
                // 4) Bulkhead: 동시성(병렬 호출) 제한
                .transform(BulkheadOperator.of(bh))
                // 5) 예외 폴백: 에러 시 빈 JSON 문자열 반환
                .onErrorResume(ex -> {
                    log.error("[ChatGPT] Flux 호출 실패 – {}", ex.getMessage(), ex);
                    @SuppressWarnings("unchecked")
                    T fallback = (T) "{}";
                    return Flux.just(fallback);
                });
    }

    private <T> Mono<T> applyMonoGuards(Mono<T> mono) {
        var cb = cbRegistry.circuitBreaker("chatgpt");
        var rl = rlRegistry.rateLimiter("chatgpt");
        var tl = tlRegistry.timeLimiter("chatgpt");
        var bh = bhRegistry.bulkhead("chatgpt");

        return mono
                // 1) 회로 차단기: 연속 실패율/느린 호출이 임계치 초과 시 호출 차단
                .transform(CircuitBreakerOperator.of(cb))
                // 2) 속도 제한: 초당 호출 수를 초과하면 지연 또는 예외 발생
                .transform(RateLimiterOperator.of(rl))
                // 3) 시간 제한: 지정된 시간 내 응답 없으면 타임아웃 예외 발생
                .transform(TimeLimiterOperator.of(tl))
                // 4) 동시 호출 제한: 지정된 최대 동시 호출 수 유지
                .transform(BulkheadOperator.of(bh))
                // 5) 폴백 처리: 모든 예외를 잡아 빈 JSON("{}")을 반환
                .onErrorResume(ex -> {
                    log.error("[ChatGPT] Mono 호출 실패 – {}", ex.getMessage(), ex);
                    @SuppressWarnings("unchecked")
                    T fallback = (T) "{}";
                    return Mono.just(fallback);
                });
    }
}