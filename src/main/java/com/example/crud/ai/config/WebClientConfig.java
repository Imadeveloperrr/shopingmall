package com.example.crud.ai.config;

import com.example.crud.common.utility.MaskingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
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
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10))
                .compress(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(http))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
