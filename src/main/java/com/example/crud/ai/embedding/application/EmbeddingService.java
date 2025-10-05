package com.example.crud.ai.embedding.application;

import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 상품 임베딩 생성 및 저장 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final ProductRepository productRepository;
    private final ProductEmbeddingCommandService productEmbeddingCommandService;

    /**
     * 임베딩이 누락된 모든 상품의 임베딩 생성
     *
     * @return 생성된 임베딩 개수
     */
    @Transactional
    public int createMissingEmbeddings() {
        List<Product> productsWithoutEmbedding = productRepository.findProductsWithoutEmbedding();

        if (productsWithoutEmbedding.isEmpty()) {
            log.info("누락된 임베딩 없음");
            return 0;
        }

        log.info("누락된 임베딩 생성 시작: {} 개 상품", productsWithoutEmbedding.size());

        List<Long> productIds = productsWithoutEmbedding.stream()
                .map(Product::getNumber)
                .toList();

        return createBatchEmbeddings(productIds);
    }

    /**
     * 여러 상품의 임베딩을 배치로 생성
     *
     * @param productIds 임베딩을 생성할 상품 ID 목록
     * @return 성공한 상품 개수
     */
    @Transactional
    public int createBatchEmbeddings(List<Long> productIds) {
        log.info("배치 임베딩 생성 시작: {} 개 상품", productIds.size());

        int successCount = 0;
        int failCount = 0;

        for (Long productId : productIds) {
            try {
                productEmbeddingCommandService.createAndSaveEmbedding(productId);
                successCount++;

                // 진행 상황 로깅 (100개마다)
                if (successCount % 100 == 0) {
                    log.info("배치 처리 진행: {}/{} 완료",
                            successCount, productIds.size());
                }

            } catch (Exception e) {
                log.error("배치 임베딩 처리 실패: productId={}", productId, e);
                failCount++;
            }
        }

        log.info("배치 임베딩 생성 완료: 성공={}, 실패={}", successCount, failCount);
        return successCount;
    }

    /**
     * 비동기 병렬 배치 임베딩 생성 (성능 개선 버전)
     *
     * @param productIds 임베딩을 생성할 상품 ID 목록
     * @return 성공한 상품 개수를 포함한 CompletableFuture
     */
    public CompletableFuture<Integer> createBatchEmbeddingsAsync(List<Long> productIds) {
        log.info("비동기 배치 임베딩 생성 시작: {} 개 상품", productIds.size());

        List<CompletableFuture<Boolean>> futures = productIds.stream()
                .map(this::createEmbeddingAsync)
                .toList();

        // 모든 작업 완료 대기
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        return allFutures.thenApply(v -> {
            int successCount = (int) futures.stream()
                    .map(CompletableFuture::join)
                    .filter(success -> success)
                    .count();

            log.info("비동기 배치 임베딩 생성 완료: 성공={}, 실패={}",
                    successCount, productIds.size() - successCount);
            return successCount;
        });
    }

    /**
     * 단일 상품의 임베딩을 비동기로 생성
     */
    @Async
    public CompletableFuture<Boolean> createEmbeddingAsync(Long productId) {
        try {
            productEmbeddingCommandService.createAndSaveEmbedding(productId);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("비동기 임베딩 생성 실패: productId={}", productId, e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 누락된 상품 임베딩을 비동기로 생성 (성능 개선 버전)
     *
     * @return 생성된 임베딩 개수를 포함한 CompletableFuture
     */
    public CompletableFuture<Integer> createMissingEmbeddingsAsync() {
        List<Product> productsWithoutEmbedding = productRepository.findProductsWithoutEmbedding();

        if (productsWithoutEmbedding.isEmpty()) {
            log.info("누락된 임베딩 없음");
            return CompletableFuture.completedFuture(0);
        }

        log.info("누락된 임베딩 비동기 생성 시작: {} 개 상품", productsWithoutEmbedding.size());

        List<Long> productIds = productsWithoutEmbedding.stream()
                .map(Product::getNumber)
                .toList();

        return createBatchEmbeddingsAsync(productIds);
    }

}