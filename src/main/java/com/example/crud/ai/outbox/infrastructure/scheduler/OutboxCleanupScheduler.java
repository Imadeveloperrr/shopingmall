package com.example.crud.ai.outbox.infrastructure.scheduler;

import com.example.crud.ai.outbox.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxRepository outboxRepository;

    /**
     * 매일 새벽 3시 오래된 처리 완료 메시지 삭제
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldMessages() {
        try {
            // 7일 이상 된 처리 완료 메시지 삭제
            Instant cutoffTime = Instant.now().minus(7, ChronoUnit.DAYS);
            int deletedCount = outboxRepository.deleteOldSentMessages(cutoffTime);

            if (deletedCount > 0) {
                log.info("Cleaned up {} old outbox messages older than {}", deletedCount, cutoffTime);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup old outbox messages", e);
        }
    }

    /**
     * 매시간 실패한 메시지 상태 확인
     */
    @Scheduled(cron = "0 0 * * * *")
    public void checkFailedMessages() {
        Long failedCount = outboxRepository.count();
        if (failedCount > 1000) {
            log.warn("High number of pending outbox messages: {}", failedCount);
            // 알림 방송 로직 추가 예정
        }
    }
}
