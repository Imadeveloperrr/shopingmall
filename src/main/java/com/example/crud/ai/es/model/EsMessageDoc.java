package com.example.crud.ai.es.model;

import com.example.crud.ai.conversation.domain.event.MsgCreatedPayload;
import com.example.crud.enums.MessageType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

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
@Setting(settingPath = "/es/settings_message.json")
@Mapping(mappingPath = "/es/mappings_message.json")
public class EsMessageDoc {

    @Id
    private String id;              // `${conversationId}_${messageId}`

    private Long conversationId;
    private Long messageId;
    private Long userId;            // 사용자 ID 추가
    private MessageType type;
    private String content;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant timestamp;

    /*── 정적 팩토리 ───*/
    public static EsMessageDoc from(MsgCreatedPayload p, Long userId) {
        return EsMessageDoc.builder()
                .id(p.conversationId() + "_" + p.messageId())
                .conversationId(p.conversationId())
                .messageId(p.messageId())
                .userId(userId)     // 사용자 ID 설정
                .type(p.type())
                .content(p.content())
                .timestamp(Instant.now())
                .build();
    }
}