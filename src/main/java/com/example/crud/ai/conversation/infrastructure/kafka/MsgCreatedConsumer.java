package com.example.crud.ai.conversation.infrastructure.kafka;

import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.ai.es.model.EsMessageDoc;
import com.example.crud.common.utility.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MsgCreatedConsumer {

    private final ElasticsearchOperations esOps;

    @KafkaListener(topics = "conv-msg-created", groupId = "es-sync")
    public void handle(String json) {
        MsgCreatedPayload p = Json.decode(json, MsgCreatedPayload.class);
        esOps.save(EsMessageDoc.from(p));
        log.debug("[ES] indexed message {}", p.messageId());
    }
}