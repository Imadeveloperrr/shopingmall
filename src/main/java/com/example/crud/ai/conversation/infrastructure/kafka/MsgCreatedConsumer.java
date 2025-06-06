package com.example.crud.ai.conversation.infrastructure.kafka;

import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.es.model.EsMessageDoc;
import com.example.crud.common.utility.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "kafka.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class MsgCreatedConsumer {

    private final ElasticsearchOperations esOps;
    private final ConversationRepository convRepo;

    @KafkaListener(topics = "conv-msg-created", groupId = "es-sync")
    public void handle(String json) {
        try {
            MsgCreatedPayload p = Json.decode(json, MsgCreatedPayload.class);

            // conversationId로 userId 조회
            Long userId = convRepo.findById(p.conversationId())
                    .map(conv -> conv.getMember().getNumber())
                    .orElse(null);

            if (userId == null) {
                log.warn("대화 {} 에 대한 사용자를 찾을 수 없음", p.conversationId());
                userId = 0L; // 기본값
            }

            // userId를 포함하여 ES 문서 생성
            EsMessageDoc doc = EsMessageDoc.from(p, userId);
            esOps.save(doc);

            log.debug("[ES] indexed message {} for user {}", p.messageId(), userId);
        } catch (Exception e) {
            log.error("[ES] Failed to process message: {}", e.getMessage(), e);
        }
    }
}