package com.example.crud.data.ai.service;

import com.example.crud.entity.ConversationMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ChatGPTIntegrationService {
    CompletableFuture<String> extractUserPreferenceFromChatGPTAsync(List<ConversationMessage> messages, String prompt);
    CompletableFuture<String> extractUserPreferenceFromHFAsync(List<ConversationMessage> messages, String prompt);

}
