package com.example.crud.ai.outbox.infrastructure;

import com.example.crud.ai.outbox.domain.entity.Outbox;
import com.example.crud.ai.conversation.infrastructure.persistence.OutboxRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final KafkaTemplate<String, String> kafka;
    private final OutboxRepository repo;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${outbox.dispatcher.batch-size:500}")
    private int batchSize;

    @Value("${outbox.dispatcher.timeout-seconds:2}")
    private int timeoutSeconds;

    // 백오프 전략을 위한 변수


    /** 설정된 주기로 미전송 메시지를 Kafka로 전송 */
    @Scheduled(fixedDelayString = "${outbox.dispatcher.delay:200}")
    public void flush() {
        try {
            // 동시에 여러 인스턴스가 실행되는 것을 방지하기 위한 로깅
            log.debug("[Outbox] Starting dispatch cycle");

            // 미전송 레코드 조회 (트랜잭션 분리)
            List<Outbox> batch = pollUnsent();

            if (batch.isEmpty()) {
                return;
            }

            // Kafka 전송 및 상태 업데이트 (별도 로직)
            processOutboxBatch(batch);
        } catch (Exception e) {
            log.debug("[Outbox] No messages to dispatch or error occurred: {}", e.getMessage());
        }

    }

    /**
     * 미전송 레코드를 조회하는 메서드 (DB 트랜잭션)
     */
    @Transactional(readOnly = true)
    public List<Outbox> pollUnsent() {
        try {
            return repo.pollUnsent(batchSize);
        } catch (Exception e) {
            log.error("[Outbox] Failed to poll unsent messages: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 각 Outbox 메시지를 Kafka로 전송하고 성공한 메시지만 상태 업데이트
     */
    public void processOutboxBatch(List<Outbox> batch) {
        List<Long> successIds = new ArrayList<>();

        for (Outbox ob : batch) {
            try {
                CompletableFuture<SendResult<String, String>> fut =
                        kafka.send(ob.getTopic(), ob.getPayload());

                SendResult<String, String> res = fut.get(timeoutSeconds, TimeUnit.SECONDS);
                RecordMetadata m = res.getRecordMetadata();
                log.debug("[Outbox] sent to Kafka {}-{} offset {}", m.topic(), m.partition(), m.offset());

                // 성공한 메시지 ID 추가
                successIds.add(ob.getId());
            } catch (Exception e) {
                log.error("[Outbox] Kafka send fail id={}: {}", ob.getId(), e.getMessage());
                // 실패한 메시지는 다음 실행 시 재처리
            }
        }

        // 성공한 메시지만 상태 업데이트 (별도 트랜잭션)
        if (!successIds.isEmpty()) {
            updateSentStatus(successIds);
        }
    }

    /**
     * 성공한 메시지의 상태를 업데이트 (DB 트랜잭션)
     */
    @Transactional
    public void updateSentStatus(List<Long> ids) {
        try {
            repo.markSent(ids, Instant.now());
            log.debug("[Outbox] marked {} messages as sent", ids.size());
        } catch (Exception e) {
            log.error("[Outbox] Failed to update message status: {}", e.getMessage(), e);
        }
    }
}