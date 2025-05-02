package com.example.crud.ai.conversation.domain.entity;

import com.example.crud.entity.Member;
import com.example.crud.enums.ConversationStatus;
import com.example.crud.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.Collections;

@Entity
@Table(
        name = "conversation",
        indexes = {
                @Index(name = "idx_conv_member", columnList = "member_id"),
                @Index(name = "idx_conv_update", columnList = "lastUpdated")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Conversation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*── 회원 연관 ──*/
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime startTime;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Version                       // ▶ Optimistic Lock
    private Integer version;

    /*── 메시지 컬렉션 ──*/
    @Builder.Default
    @OneToMany(
            mappedBy = "conversation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("timestamp ASC")         // 정렬 보장
    private List<ConversationMessage> messages = new ArrayList<>();

    /*================ 비즈니스 메서드 ================*/

    /** 메시지를 추가하고, 양방향 연관 & 타임스탬프 업데이트 */
    public ConversationMessage addMessage(MessageType type, String content) {
        ConversationMessage msg = ConversationMessage.of(this, type, content);
        this.messages.add(msg);
        this.lastUpdated = LocalDateTime.now();
        return msg;
    }

    /** 읽기 전용 콜렉션 반환 (수정 불가) */
    public List<ConversationMessage> getMessagesUnmodifiable() {
        return Collections.unmodifiableList(messages);
    }
}
