package com.example.crud.ai.conversation.domain.event;

import org.springframework.context.ApplicationEvent;

/**
 * 메시지 생성 이벤트 - Spring Events 사용
 */
public class MessageCreatedEvent extends ApplicationEvent {

    private final Long messageId;
    private final Long conversationId;
    private final Long userId;
    private final String content;
    private final String messageType;

    public MessageCreatedEvent(Object source, Long messageId, Long conversationId, Long userId, String content, String messageType) {
        super(source);
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.userId = userId;
        this.content = content;
        this.messageType = messageType;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public String getMessageType() {
        return messageType;
    }
}