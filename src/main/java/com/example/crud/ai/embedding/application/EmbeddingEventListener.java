package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.event.ProductCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상품 관련 이벤트를 수신하여 임베딩 생성을 요청하는 리스너
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingEventListener {

    private final ProductEmbeddingCommandService embeddingService;

    /**
    * 상품 생성 이벤트 처리
    */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductCreated(ProductCreatedEvent event) {
        try {
            log.debug("상품 생성 이벤트 수신: productId={}", event.getProductId());

            // EmbeddingService에 임베딩 생성 위임
            embeddingService.createAndSaveEmbedding(event.getProductId());

            log.info("상품 임베딩 생성 완료: productId={}", event.getProductId());

        } catch (Exception e) {
            // 임베딩 생성 실패해도 상품은 이미 저장됨
            // 로그만 남기고 정상 종료 (예외 전파 안 함)
            log.error("상품 임베딩 생성 실패: productId={}, error={}",
                    event.getProductId(), e.getMessage(), e);
        }
    }

}
