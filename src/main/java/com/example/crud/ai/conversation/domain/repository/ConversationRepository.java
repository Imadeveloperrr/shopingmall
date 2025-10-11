package com.example.crud.ai.conversation.domain.repository;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {


    /**
     목적 : 다수 사용자가 동일 대화를 동시에 업데이트할 때 Race Condition 방지

     구현 : JPQL + @Lock(PESSIMISTIC_WRITE) → 해당 row 를 트랜잭션 동안 배타 잠금
     RDB가 PostgreSQL 이므로 row-level lock 지원
     충돌 시 두번째 트랜잭션은 lock wait (lock_timeout) 후 예외
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdWithLock(@Param("id") @NonNull Long id);

    @EntityGraph(attributePaths = {"member"})
    Optional<Conversation> findByIdWithMember(Long id);
}
