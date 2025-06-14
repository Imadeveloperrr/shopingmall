package com.example.crud.ai.outbox.infrastructure.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OutboxMetrics {

    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public OutboxMetrics(MeterRegistry registry) {
        this.processedCounter = Counter.builder("outbox.messages.processed")
                .description("Total number of succesfully processed outbox messages")
                .register(registry);

        this.failedCounter = Counter.builder("outbox.messages.failed")
                .description("Total number of failed outbox messages")
                .register(registry);

        this.processingTimer = Timer.builder("outbox.processing.time")
                .description("TIme taken to process outbox messages")
                .register(registry);
    }

    public void recordProcessed(int count) {
        processedCounter.increment(count);
        log.debug("Recorded {} processed messages", count);
    }

    public void recordFailed(int count) {
        failedCounter.increment(count);
        log.debug("Recorded {} failed messages", count);
    }

    public Timer.Sample StartTimer () {
        return Timer.start();
    }

    public void recordTime(Timer.Sample sample) {
        sample.stop(processingTimer);
    }
}
