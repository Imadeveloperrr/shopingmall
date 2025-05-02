package com.example.crud.ai.conversation.infrastructure.persistence;

import com.example.crud.ai.outbox.domain.entity.Outbox;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /*──── 미전송 레코드 선착순 n개를 배타 잠금하고 건너뛴다 ────*/
    @Query(value = """
        SELECT * FROM outbox
        WHERE sent = false
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<Outbox> pollUnsent(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE Outbox o SET o.sent=true, o.sentAt=current_timestamp WHERE o.id IN :ids")
    void markSent(@Param("ids") List<Long> ids);
}