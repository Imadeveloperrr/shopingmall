package com.example.crud.ai.outbox.infrastructure;

import com.example.crud.ai.outbox.domain.entity.Outbox;
import com.example.crud.ai.outbox.domain.repository.OutboxRepository;
import com.example.crud.ai.outbox.infrastructure.monitoring.OutboxMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final KafkaTemplate<String, String> kafka;
    private final OutboxRepository repo;
    private final RedisTemplate<String, String> redisTemplate;
    private final OutboxMetrics metrics;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final String OUTBOX_LOCK_KEY = "outbox:dispatcher:lock";

    @Scheduled(fixedDelayString = "${outbox.dispatcher.delay:5000}")
    public void dispatch() {
        // 이미 실행 중이면 스킵
        if (!isRunning.compareAndSet(false, true)) {
            return;
        }

        // 분산 락 획득
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(OUTBOX_LOCK_KEY, "locked", Duration.ofMinutes(1));
        // true 락 획득 성공, false 락 획득 실패

        if (!Boolean.TRUE.equals(lockAcquired)) {
            isRunning.set(false);
            return;
        }

        try {
            processMessages();
        } finally {
            redisTemplate.delete(OUTBOX_LOCK_KEY);
            isRunning.set(false);
        }
    }

    private void processMessages() {
        Long pendingCount = repo.countPendingMessages();

        if (pendingCount == 0) {
            log.trace("No pending messages");
            return;
        }

        log.info("Processing {} pending messages", pendingCount);

        int totalProcessed = 0;
        int totalFailed = 0;

        while (totalProcessed + totalFailed < pendingCount) {
            List<Outbox> batch = repo.pollUnsent(100);
            if (batch.isEmpty()) break;

            Map<Boolean, Integer> results = processBatch(batch);
            totalProcessed += results.getOrDefault(true, 0);
            totalFailed += results.getOrDefault(false, 0);
        }

        // 매트릭 기록
        metrics.recordProcessed(totalProcessed);
        metrics.recordFailed(totalFailed);

        log.info("Dispatch complete: processed={}, failed={}", totalProcessed, totalFailed);
    }

    private Map<Boolean, Integer> processBatch(List<Outbox> batch) {
        Map<Long, CompletableFuture<SendResult<String, String>>> futures = new HashMap<>();
        // CompletableFuture : 비동기 작업의 결과를 나타내는 객체
        // SendResult : Kafka 전송 결과 정보. ( 어느 파티션에 저장 되었는지 등 )
        // Kafka로 비동기 전송
        for (Outbox msg : batch) {
            try {
                CompletableFuture<SendResult<String, String>> future = kafka.send(
                        msg.getTopic(),
                        String.valueOf(msg.getId()), // 파티션 키
                        msg.getPayload()
                );
                futures.put(msg.getId(), future);
            } catch (Exception e) {
                log.error("Failed to send message {}: {}", msg.getId(), e.getMessage());
                futures.put(msg.getId(), CompletableFuture.failedFuture(e));
            }
        }

        // 결과 수집
        List<Long> successIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        // allOf : 메시지가 다 도착할때까지 대기, join : 모든 메시지가 도착할때가지 스레드 멈춤.
        /**
         * allOf : 메시지가 다 도착할때가지 대기
         * join : 기다리는 행동. 스레드 멈춤
         * allOf 메서드는 배열울 요구하기 때문에 toArray로 Collection을 배열로 변환하는 과정.
         * futures.values() : Map에서 모든 CompletableFuture 객체들을 꺼내는 과정
         *
         * 즉 모든 전송이 완료될떄까지 대기
         */
        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();

        futures.forEach((id, future) -> {
            try {
                future.get(1, TimeUnit.SECONDS);
                successIds.add(id);
            } catch (Exception e) {
                failedIds.add(id);
                log.warn("Message: {} failed: {}", id, e.getMessage());
            }
        });

        // DB 업데이트
        if (!successIds.isEmpty()) {
            updateSuccess(successIds);
        }
        if (!failedIds.isEmpty()) {
            updateFailed(failedIds);
        }

        Map<Boolean, Integer> results = new HashMap<>();
        results.put(true, successIds.size());
        results.put(false, failedIds.size());
        return results;
    }

    @Transactional
    protected void updateSuccess(List<Long> ids) {
        repo.markSent(ids, Instant.now());
    }

    @Transactional
    protected void updateFailed(List<Long> ids) {
        repo.markFailed(ids, "Kafka send failed", Instant.now());
    }
}