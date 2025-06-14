package com.example.crud.ai.outbox.domain.repository;

import com.example.crud.ai.outbox.domain.entity.Outbox;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    // COUNT 쿼리 추가 (가벼운 체크)
    @Query("SELECT COUNT(o) FROM Outbox o WHERE o.sent = false AND o.retryCount < 3")
    Long countPendingMessages();

    /*──── 미전송 레코드 선착순 n개를 배타 잠금하고 건너뛴다 ────*/
    @Query(value = """
            SELECT * FROM outbox
            WHERE sent = false
            AND retry_count < 3
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Outbox> pollUnsent(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE Outbox o SET o.sent=true, o.sentAt=:now WHERE o.id IN :ids")
    void markSent(@Param("ids") List<Long> ids, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Outbox o SET o.retryCount = o.retryCount + 1, " +
            "o.errorMessage = :error, o.lastFailedAt = :failedAt " +
            "WHERE o.id IN :ids")
    void markFailed(@Param("ids") List<Long> ids,
                    @Param("error") String error,
                    @Param("failedAt") Instant failedAt);

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.sent = true AND o.sentAt < :before")
    int deleteOldSentMessages(@Param("before") Instant before);
}