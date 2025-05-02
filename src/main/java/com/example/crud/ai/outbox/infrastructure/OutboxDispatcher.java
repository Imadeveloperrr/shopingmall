package com.example.crud.ai.outbox.infrastructure;

import com.example.crud.ai.outbox.domain.entity.Outbox;
import com.example.crud.ai.conversation.infrastructure.persistence.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final KafkaTemplate<String, String> kafka;
    private final OutboxRepository              repo;

    /** 0.2 초 주기로 최대 500 건씩 Outbox → Kafka 전송 */
    @Scheduled(fixedDelay = 200)
    @Transactional                        // ▶ same Tx: poll → send → markSent
    public void flush() {

        /* ① 미전송 레코드 락 걸고 가져오기 */
        List<Outbox> batch = repo.pollUnsent(500);     // ← Size.of(x) 제거, int 파라미터

        if (batch.isEmpty()) return;

        /* ② Kafka 전송 (sync) */
        boolean allOk = true;
        for (Outbox ob : batch) {
            try {
                // send() 는 기본적으로 async; get(timeout) 으로 성공 확인
                CompletableFuture<SendResult<String,String>> fut =
                        kafka.send(ob.getTopic(), ob.getPayload());
                SendResult<String,String> res = fut.get(2, TimeUnit.SECONDS);  // timeout=2s
                RecordMetadata m = res.getRecordMetadata();
                log.debug("[Outbox] sent to Kafka {}-{} offset {}", m.topic(), m.partition(), m.offset());
            } catch (Exception e) {
                allOk = false;
                log.error("[Outbox] Kafka send fail id={} → {}", ob.getId(), e.getMessage());
                // 실패한 경우 해당 레코드 그대로 두고 루프 계속
            }
        }

        /* ③ 성공한 건만 sent=true 업데이트 */
        if (allOk) {
            repo.markSent(batch.stream().map(Outbox::getId).toList());
        } else {
            // 일부 실패 ⇒ 성공한 id 집합만 업데이트
            List<Long> okIds = batch.stream()
                    .filter(Outbox::isSent)        // KafkaTemplate 성공 콜백에서 flag set하도록 해도 됨
                    .map(Outbox::getId)
                    .toList();
            if (!okIds.isEmpty()) repo.markSent(okIds);
        }
    }
}
