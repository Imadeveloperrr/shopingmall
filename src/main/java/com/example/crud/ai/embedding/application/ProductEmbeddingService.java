package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 상품 임베딩 생성 및 관리 서비스
 * - 상품 등록/수정 시 자동으로 임베딩 생성
 * - 배치로 기존 상품들의 임베딩 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final ProductRepository productRepository;

    /**
     * 단일 상품의 임베딩 생성 및 저장
     */
    @Transactional
    public void createAndSaveEmbedding(Product product) {
        try {
            // 상품 설명을 기반으로 임베딩 텍스트 생성
            String embeddingText = buildEmbeddingText(product);

            // ML 서비스를 통해 임베딩 생성
            float[] embedding = embeddingClient.embed(embeddingText)
                    .block(Duration.ofSeconds(5));

            if (embedding != null && embedding.length > 0) {
                product.setDescriptionVector(embedding);
                productRepository.save(product);
                log.info("임베딩 생성 완료: 상품 ID {}", product.getNumber());
            } else {
                log.info("임베딩 생성 실패: 상품 ID {}", product.getNumber());
            }
        } catch (Exception e) {
            log.error("임베딩 생성 중 오류 발생: 상품 ID {}", product.getNumber(), e);
        }
    }

    /**
     * 비동기로 임베딩 생성 (상품 등록 시 응답 지연 방지)
     */
    @Async
    @Transactional
    public void createAndSaveEmbeddingAsync(Long productId) {
        productRepository.findById(productId).ifPresent(this::createAndSaveEmbedding);
    }

    /**
     * 임베딩이 없는 모든 상품에 대해 임베딩 생성 (배치 작업)
     */
    @Transactional
    public void createMissingEmbeddings() {
        List<Product> productsWithoutEmbedding = productRepository.findByDescriptionVectorIsNull();

        log.info("임베딩 생성 대상 상품 수: {}", productsWithoutEmbedding.size());

        for (Product product : productsWithoutEmbedding) {
            createAndSaveEmbedding(product);
            // 과도한 요청 방지를 위한 딜레이
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }


    /**
     * 상품 정보를 기반으로 임베딩용 텍스트 생성
     */
    private String buildEmbeddingText(Product product) {
        StringBuilder text = new StringBuilder();

        // 카테고리 정보
        text.append(product.getCategory().getGroupName()).append(" ");
        if (product.getSubCategory() != null) {
            text.append(product.getSubCategory()).append(" ");
        }

        // 브랜드와 상품명
        text.append(product.getBrand()).append(" ");
        text.append(product.getName()).append(" ");

        // 짧은 소개
        text.append(product.getIntro()).append(" ");

        // 상세 설명 (최대 500자)
        String desc = product.getDescription();
        if (desc.length() > 500) {
            desc = desc.substring(0, 500);
        }
        text.append(desc);

        // 색상과 사이즈 정보 추가
        if (!product.getProductOptions().isEmpty()) {
            text.append(" 색상: ");
            product.getProductOptions().stream()
                    .map(opt -> opt.getColor())
                    .distinct()
                    .forEach(color -> text.append(color).append(" "));

            text.append(" 사이즈: ");
            product.getProductOptions().stream()
                    .map(opt -> opt.getSize())
                    .distinct()
                    .forEach(size -> text.append(size).append(" "));
        }

        return text.toString().trim();
    }

}
