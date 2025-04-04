package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.service.ChatGPTIntegrationService;
import com.example.crud.entity.ConversationMessage;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class ChatGPTIntegrationServiceImpl implements ChatGPTIntegrationService {

    private final WebClient webClient;

    public ChatGPTIntegrationServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }
    // ChatGPT API 설정
    @Value("${chatgpt.api.url}")
    private String chatGptApiUrl;
    @Value("${chatgpt.api.key}")
    private String chatGptApiKey;

    @Value("${hf.keywords.api.url}")
    private String hfApiUrl;
    @Value("${hf.api.key}")
    private String hfApiKey;

    /**
     * OpenAI의 ChatGPT API를 호출하여, 대화 이력과 현재 프롬프트를 기반으로 사용자 선호 정보를 추출합니다.
     * 실제 업무에서는 OpenAI ChatGPT API의 채팅 형식에 맞게 모델, 메시지 리스트 등을 구성합니다.
     *
     * @param messages 기존 대화 이력 리스트
     * @param prompt   현재 사용자 메시지(프롬프트)
     * @return 비동기 결과로 반환되는 문자열 (예: 정밀 매칭 점수 또는 ChatGPT의 응답 내용)
     */
    @Override
    public CompletableFuture<String> extractUserPreferenceFromChatGPTAsync(List<ConversationMessage> messages, String prompt) {
        // OpenAI ChatGPT API의 메시지 형식에 맞춰 변환
        List<Map<String, String>> openAIMessages = new ArrayList<>();

        // (필요에 따라) 시스템 메시지 추가
        openAIMessages.add(Map.of("role", "system", "content", "You are a helpful assistant."));

        // 기존 대화 이력을 OpenAI 메시지 형식으로 변환
        for (ConversationMessage cm : messages) {
            String role = "user";
            if (cm.getMessageType() != null && cm.getMessageType() == MessageType.ASSISTANT) {
                role = "assistant";
            }
            openAIMessages.add(Map.of("role", role, "content", cm.getContent()));
        }

        // 현재 프롬프트를 추가
        openAIMessages.add(Map.of("role", "user", "content", prompt));

        // OpenAI API 요청 본문 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-4o");
        requestBody.put("messages", openAIMessages);
        requestBody.put("temperature", 0.7);

        return webClient.post()
                .uri(chatGptApiUrl)
                .header("Authorization", "Bearer " + chatGptApiKey) // Bearer 뒤에 공백 주의
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class) // 응답을 Map 형태로 수신
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("ChatGPT API 호출 오류: {}", error.getMessage()))
                .map(response -> {
                    // OpenAI 응답 구조 예시:
                    // { "choices": [ { "message": { "role": "assistant", "content": "응답 내용" } } ], ... }
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
                    return "0.0"; // 기본값 반환
                })
                .onErrorReturn("0.0")
                .toFuture();
    }

    /**
     * Hugging Face API를 호출하여, 메시지와 프롬프트를 기반으로 사용자 선호 정보를 추출합니다.
     *
     * @param messages 대화 이력 리스트
     * @param prompt   사용자 메시지 및 프롬+
     *
     *                 프트
     * @return 비동기 결과로 반환되는 JSON 문자열
     */
    @Override
    public CompletableFuture<String> extractUserPreferenceFromHFAsync(List<ConversationMessage> messages, String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("messages", messages);

        return webClient.post()
                .uri(hfApiUrl)
                .header("Authorization", "Bearer " + hfApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("Hugging Face API 호출 오류: {}", error.getMessage()))
                .onErrorReturn("{}")
                .toFuture();
    }

}
