package com.example.crud.ai.embedding.application;

import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 상품 임베딩 대량 생성 서비스 (테스트/관리자용)
 *
 * 동기 순차 vs 비동기 병렬 처리 성능 비교용
 * - 동기 순차: for loop으로 1개씩 처리 (100초)
 * - 비동기 병렬: CompletableFuture로 동시 처리 (5초)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final ProductRepository productRepository;
    private final ProductEmbeddingCommandService productEmbeddingCommandService;

    // ==================== 동기 순차 처리 (비교 기준) ====================

    /**
     * [동기 순차] 누락된 임베딩 생성
     * - for loop으로 순차 처리
     * - 100개 상품 × 1초 = 100초
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

        log.info("[동기 순차] 누락된 임베딩 생성 시작: {} 개 상품", productsWithoutEmbedding.size());

        List<Long> productIds = productsWithoutEmbedding.stream()
                .map(Product::getNumber)
                .toList();

        return createBatchEmbeddings(productIds);
    }

    /**
     * [동기 순차] 여러 상품 임베딩 순차 생성
     * - for loop으로 1개씩 순차 처리
     *
     * @param productIds 임베딩을 생성할 상품 ID 목록
     * @return 성공한 상품 개수
     */
    @Transactional
    public int createBatchEmbeddings(List<Long> productIds) {
        log.info("[동기 순차] 임베딩 순차 생성 시작: {} 개 상품", productIds.size());

        int successCount = 0;
        int failCount = 0;

        for (Long productId : productIds) {
            try {
                productEmbeddingCommandService.createAndSaveEmbedding(productId);
                successCount++;

                // 진행 상황 로깅 (100개마다)
                if (successCount % 100 == 0) {
                    log.info("순차 처리 진행: {}/{} 완료", successCount, productIds.size());
                }

            } catch (Exception e) {
                log.error("임베딩 순차 처리 실패: productId={}", productId, e);
                failCount++;
            }
        }

        log.info("[동기 순차] 임베딩 순차 생성 완료: 성공={}, 실패={}", successCount, failCount);
        return successCount;
    }

    // ==================== 비동기 병렬 처리 (성능 개선) ====================

    /**
     * [비동기 병렬] 누락된 임베딩 생성
     * - CompletableFuture로 병렬 처리
     * - 100개 상품 ÷ 20 스레드 = 5초
     *
     * @return 생성된 임베딩 개수를 포함한 CompletableFuture
     */
    public CompletableFuture<Integer> createMissingEmbeddingsAsync() {
        List<Product> productsWithoutEmbedding = productRepository.findProductsWithoutEmbedding();

        if (productsWithoutEmbedding.isEmpty()) {
            log.info("누락된 임베딩 없음");
            return CompletableFuture.completedFuture(0);
        }

        log.info("[비동기 병렬] 누락된 임베딩 생성 시작: {} 개 상품", productsWithoutEmbedding.size());

        List<Long> productIds = productsWithoutEmbedding.stream()
                .map(Product::getNumber)
                .toList();

        return createBatchEmbeddingsAsync(productIds);
    }

    /**
     * [비동기 병렬] 여러 상품 임베딩 병렬 생성
     * - CompletableFuture로 동시에 여러 개 처리
     *
     * @param productIds 임베딩을 생성할 상품 ID 목록
     * @return 성공한 상품 개수를 포함한 CompletableFuture
     */
    public CompletableFuture<Integer> createBatchEmbeddingsAsync(List<Long> productIds) {
        log.info("[비동기 병렬] 임베딩 병렬 생성 시작: {} 개 상품", productIds.size());

        // CompletableFuture.supplyAsync로 병렬 처리
        List<CompletableFuture<Boolean>> futures = productIds.stream()
                .map(productId ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            productEmbeddingCommandService.createAndSaveEmbedding(productId);
                            return true;
                        } catch (Exception e) {
                            log.error("비동기 임베딩 생성 실패: productId={}", productId, e);
                            return false;
                        }
                    })
                )
                .toList();

        // 모든 작업 완료 후 성공 개수 계산
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    int successCount = (int) futures.stream()
                            .map(CompletableFuture::join)
                            .filter(success -> success)
                            .count();

                    log.info("[비동기 병렬] 임베딩 병렬 생성 완료: 성공={}, 실패={}",
                            successCount, productIds.size() - successCount);
                    return successCount;
                });
    }

}