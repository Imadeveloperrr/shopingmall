package com.example.crud.ai.embedding.application;

import com.example.crud.ai.common.VectorFormatter;
import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.ai.embedding.domain.ProductTextBuilder;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

            float[] embedding = embeddingApiClient.generateEmbeddingAsync(productText).join();
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
            throw new RuntimeException("임베딩 생성 실패: " + cause.getMessage(), cause);
        } catch (IllegalArgumentException e) {
            log.error("임베딩 생성 실패: productId={}", productId, e);
            throw e;
        } catch (Exception e) {
            log.error("임베딩 생성 실패: productId={}", productId, e);
            throw new RuntimeException("임베딩 생성 실패: " + e.getMessage(), e);
        }
    }
}
