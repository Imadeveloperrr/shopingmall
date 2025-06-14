package com.example.crud.ai.conversation.application.command;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.outbox.domain.entity.Outbox;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.outbox.domain.repository.OutboxRepository;
import com.example.crud.common.utility.Json;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationCommandService {

    private final ConversationRepository       convRepo;
    private final OutboxRepository             outboxRepo;
    private final Clock                        clock = Clock.systemUTC();

    @Transactional
    public void addMessage(long convId, MessageType type, String content) {

        Conversation conv = convRepo.findByIdWithLock(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        ConversationMessage msg = conv.addMessage(type, content);
        convRepo.save(conv);            // Optimistic Lock 버전 증가

        /*── Outbox row insert (같은 Tx) ──*/
        String json = Json.encode(new MsgCreatedPayload(
                msg.getId(), convId, type, content));
        outboxRepo.save(Outbox.of("conv-msg-created", json, Instant.now(clock)));

        log.debug("[Conversation] msg {} queued to outbox", msg.getId());
    }
}