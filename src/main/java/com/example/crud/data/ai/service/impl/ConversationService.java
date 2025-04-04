package com.example.crud.data.ai.service.impl;

import com.example.crud.entity.Conversation;
import com.example.crud.entity.ConversationMessage;
import com.example.crud.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.crud.enums.MessageType;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;

    /**
     * 지정된 대화(conversationId)에 메시지를 추가합니다.
     */
    public void addMessage(Long conversationId, MessageType messageType, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));
        ConversationMessage message = ConversationMessage.builder()
                .conversation(conversation)
                .messageType(messageType)
                .content(content)
                .build();
        conversation.getMessages().add(message);
        conversationRepository.save(conversation);
    }

    /**
    * 지정된 대화의 모든 메시지를 반환합니다.
    */
    public List<ConversationMessage> getConversationMessages(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));
        return conversation.getMessages();
    }
}
