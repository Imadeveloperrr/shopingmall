package com.example.crud.ai.embedding.infrastructure;

import com.example.crud.ai.embedding.application.ProductEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 임베딩 생성 배치 스케줄러
 * - 애플리케이션 시작 시 누락된 임베딩 생성
 * - 주기적으로 누락된 임베딩 확인 및 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingBatchScheduler {

    private final ProductEmbeddingService embeddingService;

    /**
     * 애플리케이션 시작 후 초기 임베딩 생성
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("애플리케이션 시작 - 누락된 임베딩 확인 중...");
        try {
            embeddingService.createMissingEmbeddings();
        } catch (Exception e) {
            log.error("초기 임베딩 생성 실패", e);
        }
    }

    /**
     * 매일 새벽 3시에 누락된 임베딩 생성
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void createMissingEmbeddingsScheduled() {
        log.info("정기 임베딩 생성 작업 시작");
        try {
            embeddingService.createMissingEmbeddings();
        } catch (Exception e) {
            log.error("정기 임베딩 생성 실패", e);
        }
    }
}
