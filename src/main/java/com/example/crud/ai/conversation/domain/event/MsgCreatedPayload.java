package com.example.crud.ai.conversation.domain.event;

import com.example.crud.enums.MessageType;

public record MsgCreatedPayload(
        Long   messageId,
        Long   conversationId,
        MessageType type,
        String content
) {}
