package com.example.crud.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chatgpt")
public record ChatGptProperties (
    String apiUrl, // api Endpoint
    String apiKey,
    String model,
    String authType, // 인증 유형 (예: Bearer)
    boolean compress, // 압축 사용 여부
    double temperature, // 창의성 수준 (0.0 ~ 1.0)
    int timeoutSec, // 타임아웃 설정
    int rateLimitPerSec, // 초당 요청 제한
    int streamChunkLimit // 스트림 청크 제한
) {}
