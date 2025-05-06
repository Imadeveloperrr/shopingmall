package com.example.crud.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chatgpt")
public record ChatGptProperties (
    String apiUrl,
    String apiKey,
    String model,
    double temperature,
    int timeoutSec,
    int rateLimitPerSec,
    int streamChunkLimit
) {}
