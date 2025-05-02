package com.example.crud.ai.es.model;

import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.enums.MessageType;
import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.annotations.Mapping;

import java.time.Instant;

/**
 * Elasticsearch 인덱스 매핑용 DTO
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "conversation-message")
@Setting(settingPath = "/es/settings_message.json")     // 선택
@Mapping(mappingPath = "/es/mappings_message.json")    // 선택
public class EsMessageDoc {

    @Id
    private String id;              // `${conversationId}_${messageId}`

    private Long conversationId;
    private Long messageId;
    private MessageType type;
    private String content;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant timestamp;

    /*── 정적 팩토리 ───*/
    public static EsMessageDoc from(MsgCreatedPayload p) {
        return EsMessageDoc.builder()
                .id(p.conversationId() + "_" + p.messageId())
                .conversationId(p.conversationId())
                .messageId(p.messageId())
                .type(p.type())
                .content(p.content())
                .timestamp(Instant.now())      // or 전달 값
                .build();
    }
}