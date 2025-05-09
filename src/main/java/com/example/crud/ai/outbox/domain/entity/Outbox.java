package com.example.crud.ai.outbox.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_status", columnList = "sent"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String topic;          // Kafka Topic 이름

    @Lob
    @Column(nullable = false)
    private String payload;        // JSON 직렬화

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant sentAt;        // null = 미전송

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;  // 전송 여부

    /*── 정적 팩토리 ──*/
    public static Outbox of(String topic, String payload, Instant now) {
        return Outbox.builder()
                .topic(topic)
                .payload(payload)
                .createdAt(now)
                .sent(false)
                .build();
    }

    public void markSent(Instant ts) {
        this.sent = true;
        this.sentAt = ts;
    }
}
