package com.example.crud.ai.conversation.domain.entity;

import com.example.crud.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_message",
        indexes = @Index(name = "idx_msg_conv_time", columnList = "conversation_id,timestamp"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ConversationMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /*──────── 정적 팩토리 ────────*/
    public static ConversationMessage of(Conversation conv, MessageType type, String content) {
        return ConversationMessage.builder()
                .conversation(conv)
                .messageType(type)
                .content(content)
                .build();
    }
}