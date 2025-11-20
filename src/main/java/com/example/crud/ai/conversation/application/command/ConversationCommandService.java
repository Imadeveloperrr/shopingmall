package com.example.crud.ai.conversation.application.command;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.conversation.domain.event.MessageCreatedEvent;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationCommandService {

    private final ConversationRepository convRepo;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void addMessage(long convId, MessageType type, String content) {

        Conversation conv = convRepo.findByIdWithLock(convId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONVERSATION_NOT_FOUND));

        ConversationMessage msg = conv.addMessage(type, content);
        convRepo.save(conv);            // Optimistic Lock 버전 증가

        // Spring Event 발행 (트랜잭션 커밋 후 처리됨)
        MessageCreatedEvent event = new MessageCreatedEvent(
                this, msg.getId(), convId, conv.getMember().getNumber(), content, type.name());
        eventPublisher.publishEvent(event);

        log.debug("[Conversation] msg {} event published", msg.getId());
    }
}
