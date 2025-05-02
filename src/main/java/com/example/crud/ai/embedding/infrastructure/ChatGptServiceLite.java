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

    /**
     * 스트리밍 방식 API 호출 메서드
     *  - Flux<String> 형태로 응답을 조각(chunk) 단위로 반환합니다.
     *  - 이미 처리된 동일 요청은 캐시에서 바로 반환합니다.
     *
     * @param history 대화 이력(이전 메시지 리스트)
     * @param prompt  새로 보낼 사용자 프롬프트
     * @return API 응답 조각을 순차적으로 발행하는 Flux<String>
     */
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

    /**
     * 단일 응답 API 호출 메서드
     *  - Mono<String> 형태로 전체 응답을 한 번에 반환합니다.
     *
     * @param history 대화 이력
     * @param prompt  사용자 프롬프트
     * @return API 응답 전체를 발행하는 Mono<String>
     */
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

    /**
     * Flux<T>에 Resilience4j 보호막을 적용하는 헬퍼 메서드
     *  - CircuitBreaker, RateLimiter, TimeLimiter, Bulkhead 순으로 적용
     *  - 보호막에서 예외가 발생하면 빈 JSON("{}")을 폴백으로 반환
     *
     * @param flux 원본 Flux<T>
     * @param <T>  발행 아이템 타입(String 사용 권장)
     * @return 보호막 적용 후 Flux<T>
     */
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

    /**
     * Mono<T>에 Resilience4j 보호막을 적용하는 헬퍼 메서드
     *  - 위 Flux용 메서드와 동일한 로직을 Mono에 적용
     */
    /**
     * Mono<T>에 Resilience4j 보호막을 적용하는 헬퍼 메서드
     *
     * 보호막 적용 순서:
     * 1. CircuitBreaker: API 실패율이 설정 값을 초과하면 이후 호출을 잠시 차단합니다.
     * 2. RateLimiter: 지정된 초당 요청 수를 초과하면 호출을 지연시키거나 예외를 발생시킵니다.
     * 3. TimeLimiter: 지정된 시간(초) 내에 응답이 없으면 타임아웃 예외를 발생시킵니다.
     * 4. Bulkhead: 동시에 실행되는 호출 수를 제한하여 시스템 과부하를 방지합니다.
     *
     * 모든 보호막 적용 후에도 예외가 발생하면 onErrorResume 블록에서
     * 에러 로그를 남기고 빈 JSON 문자열("{}")을 폴백으로 반환합니다.
     *
     * @param mono 원본 Mono<T> (API 응답을 담은 Mono)
     * @param <T>  발행되는 데이터 타입 (주로 String)
     * @return 보호막이 적용된 Mono<T>, 에러 시 폴백 Mono<String>
     */
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