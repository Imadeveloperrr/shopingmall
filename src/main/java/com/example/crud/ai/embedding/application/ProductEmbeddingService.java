package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BATCH_QUEUE_KEY = "embedding:batch:queue";

    /**
     * 비동기 임베딩 생성 (개선된 버전)
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
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));

            // 이미 임베딩 있으면 스킵
            if (product.getDescriptionVector() != null) {
                return;
            }

            // 배치 큐 추가
            redisTemplate.opsForList().rightPush(BATCH_QUEUE_KEY, productId);

            // 큐 크기 확인 후 배치 처리
            Long queueSize = redisTemplate.opsForList().size(BATCH_QUEUE_KEY);
            if (queueSize != null && queueSize >= 10) {
                processBatchEmbeddings();
            }
        } catch (Exception e) {
            log.error("Failed Embedding created: productId={}", productId, e);
        }
    }

    /**
     * 배치로 임베딩 처리
     */
    @Scheduled(fixedDelay = 30000)
    public void processBatchEmbeddings() {
        try {
            List<Object> productIds = redisTemplate.opsForList().range(BATCH_QUEUE_KEY, 0, 49); // Max 50

            if (productIds == null || productIds.isEmpty()) {
                return;
            }

            // 상품 정보 조회
            List<Product> products = productRepository.findAllById(
                    productIds.stream()
                            .map(id -> Long.parseLong(id.toString()))
                            .collect(Collectors.toList())
            );

            // 텍스트 추출
            List<String> texts = products.stream()
                    .map(this::buildEmbeddingText)
                    .collect(Collectors.toList());

            // 배치 임베딩 생성
            List<float[]> embeddings = embeddingClient.batchEmbed(texts)
                    .block(Duration.ofSeconds(10));

            if (embeddings != null && embeddings.size() == products.size()) {
                // 임베딩 저장
                for (int i = 0; i < products.size(); i++) {
                    products.get(i).setDescriptionVector(embeddings.get(i));
                }
                productRepository.saveAll(products);

                // 처리된 항목 제거
                redisTemplate.opsForList().trim(BATCH_QUEUE_KEY, productIds.size(), -1);
            }

        } catch (Exception e) {
            log.error("배치 임베딩 처리 실패", e);
        }
    }

    /**
     * 임베딩이 없는 모든 상품에 대해 임베딩 생성 (배치 작업)
     */
    @Transactional
    public void createMissingEmbeddings() {
        List<Product> productsWithoutEmbedding = productRepository.findByDescriptionVectorIsNull();

        log.info("임베딩 생성 대상 상품 수: {}", productsWithoutEmbedding.size());

        if (productsWithoutEmbedding.isEmpty()) {
            log.info("생성할 임베딩 데이터가 존재하지 않습니다.");
            return ;
        }

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
