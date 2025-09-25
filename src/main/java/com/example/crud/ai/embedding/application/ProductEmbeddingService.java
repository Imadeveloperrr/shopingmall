package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.example.crud.ai.embedding.event.ProductCreatedEvent;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * 상품 임베딩 서비스 - 상품 정보를 벡터로 변환하여 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingService {

    private final EmbeddingApiClient embeddingApiClient;
    private final ProductRepository productRepository;
    private final DescriptionRefinementService refinementService;

    // PostgreSQL 벡터 포맷용 DecimalFormat
    private static final DecimalFormat VECTOR_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        VECTOR_FORMAT = new DecimalFormat("0.########", symbols);
        VECTOR_FORMAT.setGroupingUsed(false);
    }
    
    /**
     * 상품 생성 이벤트 리스너 - 트랜잭션 커밋 후 실행
     */
    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductCreatedEvent(ProductCreatedEvent event) {
        try {
            log.debug("상품 생성 이벤트 수신: productId={}", event.getProductId());

            Product product = productRepository.findById(event.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + event.getProductId()));

            createAndSaveEmbedding(product);

        } catch (Exception e) {
            log.error("상품 임베딩 생성 실패: productId={}", event.getProductId(), e);
        }
    }

    /**
     * 단일 상품의 임베딩을 비동기로 생성 및 저장 (직접 호출용)
     */
    @Async
    @Transactional
    public void createAndSaveEmbeddingAsync(Long productNumber) {
        try {
            Product product = productRepository.findById(productNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productNumber));

            createAndSaveEmbedding(product);

        } catch (Exception e) {
            log.error("상품 임베딩 생성 실패: productNumber={}", productNumber, e);
        }
    }
    
    /**
     * 단일 상품의 임베딩 생성 및 저장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndSaveEmbedding(Product product) {
        try {
            // 원본 설명 백업
            String originalDescription = product.getDescription();

            // 상품 설명을 정제 (임베딩용 텍스트 생성에도 사용됨)
            String refinedDescription = null;
            if (originalDescription != null && !originalDescription.trim().isEmpty()) {
                refinedDescription = refinementService.refineProductDescription(originalDescription);
                log.info("상품 설명 정제: productId={}, 원본={}자 -> 정제={}자",
                        product.getNumber(), originalDescription.length(), refinedDescription.length());
            }

            // 정제된 설명으로 상품 텍스트 생성
            String productText = buildProductText(product, refinedDescription);

            // 임베딩 벡터 생성
            float[] embedding = embeddingApiClient.generateEmbedding(productText);

            // 벡터 업데이트
            String vectorString = formatVectorForPostgreSQL(embedding);
            int vectorUpdateCount = productRepository.updateDescriptionVector(product.getNumber(), vectorString);

            if (vectorUpdateCount == 0) {
                log.error("벡터 업데이트 실패: productId={} - 0개 행 업데이트됨", product.getNumber());
                throw new RuntimeException("벡터 업데이트 실패 - 상품을 찾을 수 없음");
            }

            // 원본 설명을 정제된 설명으로 교체 (API 응답시 정제된 설명이 반환되도록)
            log.debug("설명 업데이트 조건 체크: productId={}, refinedDescription!=null:{}, originalLength:{}, refinedLength:{}",
                     product.getNumber(), refinedDescription != null,
                     originalDescription != null ? originalDescription.length() : 0,
                     refinedDescription != null ? refinedDescription.length() : 0);

            if (refinedDescription != null && !originalDescription.equals(refinedDescription)) {
                int descUpdateCount = productRepository.updateDescription(product.getNumber(), refinedDescription);
                log.info("상품 설명 업데이트: productId={}, updateCount={}", product.getNumber(), descUpdateCount);
            } else {
                log.debug("설명 업데이트 건너뜀: productId={}, refinedDescription==null:{}, equals:{}",
                         product.getNumber(), refinedDescription == null,
                         refinedDescription != null ? originalDescription.equals(refinedDescription) : "N/A");
            }

            log.info("상품 임베딩 및 설명 정제 완료: productId={}, textLength={}, vectorSize={}, vectorUpdate={}",
                     product.getNumber(), productText.length(), embedding.length, vectorUpdateCount);

        } catch (Exception e) {
            log.error("상품 임베딩 생성 실패: productId={}", product.getNumber(), e);
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }
    
    /**
     * 여러 상품의 임베딩을 배치로 생성
     */
    @Transactional
    public void createBatchEmbeddings(List<Long> productNumbers) {
        log.info("배치 임베딩 생성 시작: {} 개 상품", productNumbers.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Long productNumber : productNumbers) {
            try {
                Product product = productRepository.findById(productNumber)
                        .orElse(null);
                
                if (product != null) {
                    createAndSaveEmbedding(product);
                    successCount++;
                } else {
                    log.warn("상품을 찾을 수 없음: productNumber={}", productNumber);
                    failCount++;
                }
                
            } catch (Exception e) {
                log.error("배치 임베딩 처리 실패: productNumber={}", productNumber, e);
                failCount++;
            }
        }
        
        log.info("배치 임베딩 생성 완료: 성공={}, 실패={}", successCount, failCount);
    }
    
    /**
     * 임베딩 벡터가 없는 모든 상품의 임베딩 생성
     */
    @Transactional
    public void createMissingEmbeddings() {
        List<Product> productsWithoutEmbedding = productRepository.findProductsWithoutEmbedding();
        
        log.info("누락된 임베딩 생성 시작: {} 개 상품", productsWithoutEmbedding.size());
        
        int count = 0;
        for (Product product : productsWithoutEmbedding) {
            try {
                createAndSaveEmbedding(product);
                count++;
                
                // 100개마다 로그 출력
                if (count % 100 == 0) {
                    log.info("누락된 임베딩 생성 진행: {}/{}", count, productsWithoutEmbedding.size());
                }
                
            } catch (Exception e) {
                log.error("누락된 임베딩 생성 실패: productId={}", product.getNumber(), e);
            }
        }
        
        log.info("누락된 임베딩 생성 완료: {} 개 완료", count);
    }
    
    /**
     * 상품 정보를 텍스트로 변환
     */
    private String buildProductText(Product product) {
        return buildProductText(product, null);
    }

    /**
     * 상품 정보를 텍스트로 변환 (정제된 설명 사용)
     */
    private String buildProductText(Product product, String refinedDescription) {
        StringBuilder text = new StringBuilder();

        // 상품명
        if (product.getName() != null) {
            text.append(product.getName()).append(" ");
        }

        // 카테고리
        if (product.getCategory() != null) {
            text.append(product.getCategory().name()).append(" ");
        }

        // 상품 설명 (정제된 설명 우선 사용, 없으면 원본 사용)
        String description = refinedDescription != null ? refinedDescription : product.getDescription();
        if (description != null) {
            text.append(description).append(" ");
        }

        // 브랜드
        if (product.getBrand() != null) {
            text.append(product.getBrand()).append(" ");
        }

        // 가격대 정보
        if (product.getPrice() != null) {
            String priceRange = getPriceRange(product.getPrice());
            text.append(priceRange).append(" ");
        }

        return text.toString().trim();
    }
    
    /**
     * 가격을 가격대로 변환
     */
    private String getPriceRange(Integer price) {
        if (price < 10000) return "저가형";
        if (price < 50000) return "보급형";
        if (price < 100000) return "중급형";
        if (price < 500000) return "고급형";
        return "프리미엄";
    }

    /**
     * float[] 배열을 PostgreSQL vector 형식 문자열로 변환
     */
    private String formatVectorForPostgreSQL(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(VECTOR_FORMAT.format(vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}