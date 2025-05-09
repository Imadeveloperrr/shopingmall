package com.example.crud.ai.recommendation.domain.dto;

import com.example.crud.ai.config.ChatGptProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /* stream 여부에 따라 map 생성 */
    public Map<String, Object> toMap(boolean stream) {
        Map<String, Object> map = new HashMap<>();
        map.put("model", model);
        map.put("messages", messages);
        map.put("temperature", temperature);
        if (stream) map.put("stream", true);
        return map;
    }
}
