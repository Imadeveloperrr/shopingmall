package com.example.crud.ai.conversation.domain.repository;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    /**
     * 회원번호(member.number)를 기준으로 대화 목록을 조회
     */
    List<Conversation> findByMember_Number(Long memberNumber);

    /**
     * 지정된 conversation.id 로 Conversation 을 조회하되,
     * messages 컬렉션을 즉시 로딩(@EntityGraph)하여 함께 가져옴
     */
    @EntityGraph(attributePaths = "messages")
    Optional<Conversation> findWithMessagesById(Long id);

    /**
     목적 : 다수 사용자가 동일 대화를 동시에 업데이트할 때 Race Condition 방지

     구현 : JPQL + @Lock(PESSIMISTIC_WRITE) → 해당 row 를 트랜잭션 동안 배타 잠금
     RDB가 MariaDB 이므로 innodb row-lock 지원
     충돌 시 두번째 트랜잭션은 lock wait (innodb_lock_wait_timeout) 후 예외
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdWithLock(@Param("id") Long id);
}
