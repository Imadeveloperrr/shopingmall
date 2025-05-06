package com.example.crud.ai.conversation.domain.repository;

import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    // 기존 N+1 방지용 즉시로딩 메서드
    @EntityGraph(attributePaths = "conversation")
    List<ConversationMessage> findByConversation_IdOrderByTimestampAsc(Long conversationId);

    // 페이징용: 대화 ID 기준 최신 순으로 Page<ConversationMessage> 반환
    Page<ConversationMessage> findByConversation_Id(Long conversationId, Pageable pageable);

    // 커서 이후(이전) 메시지 조회: timestamp < before 이면서 ID DESC 조회
    Page<ConversationMessage>
    findByConversation_IdAndTimestampBefore(Long conversationId,
                                            LocalDateTime before,
                                            Pageable pageable);
}
