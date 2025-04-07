package com.example.crud.data.ai.service.impl;

import com.example.crud.entity.Conversation;
import com.example.crud.entity.ConversationMessage;
import com.example.crud.enums.MessageType;
import com.example.crud.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;

    /**
     * 지정된 대화(conversationId)에 메시지를 추가합니다.
     *
     * @param conversationId 대화 ID
     * @param messageType    메시지 유형 (예: USER, ASSISTANT)
     * @param content        메시지 내용
     */
    public void addMessage(Long conversationId, MessageType messageType, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));
        ConversationMessage message = ConversationMessage.builder()
                .conversation(conversation)
                .messageType(messageType)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        conversation.getMessages().add(message);
        conversationRepository.save(conversation);
    }

    /**
     * 지정된 대화의 모든 메시지를 반환합니다.
     *
     * @param conversationId 대화 ID
     * @return 대화 메시지 리스트
     */
    public List<ConversationMessage> getConversationMessages(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found with id: " + conversationId));
        return conversation.getMessages();
    }
}
