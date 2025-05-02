package com.example.crud.ai.recommendation.domain.dto;

import com.example.crud.ai.config.ChatGptProperties;

import java.util.List;

public record ChatMessage(String role, String content) { }

public record ChatPayload(
        String model,
        List<ChatMessage> messages,
        double temperature
) {
    public static ChatPayload build(List<ChatMessage> history, String prompt, ChatGptProperties p) {
        var msgs = new java.util.ArrayList<>(history);
        msgs.add(new ChatMessage("user", prompt));
        return new ChatPayload(p.model(), msgs, p.temperature());
    }
    /* stream 여부에 따라 map 생성 – 구현 생략 */
    public java.util.Map<String, Object> toMap(boolean stream) { /* ... */ }
}