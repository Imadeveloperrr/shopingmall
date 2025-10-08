package com.example.crud.ai.config;

import com.example.crud.common.utility.MaskingFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebClientConfig {

    private final ChatGptProperties prop;

    @Bean("chatGptClient")
    public WebClient chatGptClient() {
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(prop.timeoutSec()))
                .compress(prop.compress());

        return WebClient.builder()
                .baseUrl(prop.apiUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("Authorization", prop.authType() + " " + prop.apiKey())
                .filter(MaskingFilter.auth())
                .build();
    }
    
    @Bean("embeddingWebClient")
    public WebClient embeddingWebClient() {
        /*
        LoopResources loopResources = LoopResources.create(
        "embedding-loop",
        16,
        true); // daemon
         */
        HttpClient http = HttpClient.create() // CPU 개수 만큼 스레드 자동 생성
                //.runOn(loopResources) 커스텀 스레드풀 적용.
                .compress(true);

        return WebClient.builder()
                .baseUrl("https://api.openai.com")
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("Authorization", "Bearer " + prop.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .filter(ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
                    if (clientResponse.statusCode().is4xxClientError() ||
                        clientResponse.statusCode().is5xxServerError()) {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("OpenAI API ERROR : status={}, body={}",
                                            clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "OpenAI API FAIL : " + clientResponse.statusCode()
                                    ));
                                });
                    }
                    return Mono.just(clientResponse);
                }))
                .codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)
                )
                .build();
    }

}
