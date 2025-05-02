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
                .compress(true);

        return WebClient.builder()
                .baseUrl(prop.apiUrl())
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader("Authorization", "Bearer " + prop.apiKey())
                .filter(MaskingFilter.auth())
                .build();
    }
}
