package com.example.crud.ai.embedding.application;

import com.example.crud.ai.common.VectorFormatter;
import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.ai.embedding.domain.ProductTextBuilder;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingCommandService {

    private final ProductRepository productRepository;
    private final ProductTextBuilder productTextBuilder;
    private final EmbeddingApiClient embeddingApiClient;

    @Transactional
    public void createAndSaveEmbedding(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

            String productText = productTextBuilder.buildProductText(product);
            log.info("상품 텍스트 준비 완료: productId={}, textLength={}", productId, productText.length());

            log.info("임베딩 API 호출 시작: productId={}", productId);
            CompletableFuture<float[]> future = embeddingApiClient.generateEmbeddingAsync(productText);
            log.info("CompletableFuture 받음: productId={}, future={}", productId, future);

            log.info("join() 호출 전: productId={}", productId);
            float[] embedding = future.join();
            log.info("join() 호출 후: productId={}, embedding={}", productId, embedding);

            if (embedding == null) {
                log.error("임베딩 생성 결과가 null입니다: productId={}", productId);
                throw new IllegalStateException("임베딩 생성 결과가 null입니다");
            }

            log.debug("임베딩 생성 완료: productId={}, dimension={}", productId, embedding.length);

            String vectorString = VectorFormatter.formatForPostgreSQL(embedding);

            int updateCount = productRepository.updateDescriptionVector(productId, vectorString);
            if (updateCount == 0) {
                log.error("벡터 업데이트 실패: productId={} - 0개 행 업데이트됨", productId);
                throw new IllegalStateException("벡터 업데이트 실패 - 상품을 찾을 수 없습니다");
            }

            log.info("상품 임베딩 저장 완료: productId={}, vectorSize={}", productId, embedding.length);
        } catch (CompletionException completionException) {
            Throwable cause = completionException.getCause() != null ? completionException.getCause() : completionException;
            log.error("임베딩 생성 실패: productId={}", productId, cause);
            if (cause instanceof BaseException baseException) {
                throw baseException;
            }
            throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, cause.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("임베딩 생성 실패: productId={}", productId, e);
            throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("임베딩 생성 실패: productId={}", productId, e);
            throw new BaseException(ErrorCode.EMBEDDING_GENERATION_FAILED, e.getMessage());
        }
    }
}
