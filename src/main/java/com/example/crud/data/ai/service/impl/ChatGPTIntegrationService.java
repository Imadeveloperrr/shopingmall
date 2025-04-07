package com.example.crud.data.ai.service.impl;

import com.example.crud.entity.ConversationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class ChatGPTIntegrationService {

    private final WebClient webClient;

    public ChatGPTIntegrationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Value("${chatgpt.api.url}")
    private String chatGptApiUrl;

    @Value("${chatgpt.api.key}")
    private String chatGptApiKey;

    /**
     * ChatGPT API를 호출하여 사용자 메시지에서 상세 의도 정보를 JSON 형식으로 추출합니다.
     */
    public CompletableFuture<String> extractUserPreferenceFromChatGPTAsync(List<ConversationMessage> messages, String prompt) {
        // OpenAI ChatGPT API의 메시지 형식 구성
        List<Map<String, String>> openAIMessages = new ArrayList<>();
        openAIMessages.add(Map.of("role", "system", "content", "You are a helpful assistant."));
        for (ConversationMessage cm : messages) {
            String role = "user";
            if (cm.getMessageType() != null && cm.getMessageType().name().equals("ASSISTANT")) {
                role = "assistant";
            }
            openAIMessages.add(Map.of("role", role, "content", cm.getContent()));
        }
        openAIMessages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o",
                "messages", openAIMessages,
                "temperature", 0.7
        );

        return webClient.post()
                .uri(chatGptApiUrl)
                .header("Authorization", "Bearer " + chatGptApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("ChatGPT API 호출 오류: {}", error.getMessage()))
                .map(response -> {
                    if (response != null && response.containsKey("choices")) {
                        List choices = (List) response.get("choices");
                        if (!choices.isEmpty()) {
                            Map firstChoice = (Map) choices.get(0);
                            Map message = (Map) firstChoice.get("message");
                            if (message != null && message.containsKey("content")) {
                                return message.get("content").toString();
                            }
                        }
                    }
                    return "{}"; // 기본적으로 빈 JSON 객체 반환
                })
                .onErrorReturn("{}")
                .toFuture();
    }
}
