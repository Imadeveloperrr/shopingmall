package com.example.crud.ai.outbox.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "outbox",
        indexes = {
            @Index(name = "idx_outbox_status", columnList = "sent"),
            @Index(name = "idx_outbox_created", columnList = "createdAt")
})
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

    @Column
    private Instant lastFailedAt; // 마지막 실패날짜

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;  // 전송 여부

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;// 재시도 횟수 증가

    @Column
    private String errorMessage; // 에러 베시지 저장

    /*── 정적 팩토리 ──*/
    public static Outbox of(String topic, String payload, Instant now) {
        return Outbox.builder()
                .topic(topic)
                .payload(payload)
                .createdAt(now)
                .sent(false)
                .build();
    }

}
