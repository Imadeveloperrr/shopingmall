package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 임베딩 서비스 - 상품 정보를 벡터로 변환하여 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingService {
    
    private final EmbeddingApiClient embeddingApiClient;
    private final ProductRepository productRepository;
    
    /**
     * 단일 상품의 임베딩을 비동기로 생성 및 저장
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
    @Transactional
    public void createAndSaveEmbedding(Product product) {
        try {
            // 상품 설명 텍스트 생성
            String productText = buildProductText(product);
            
            // 임베딩 벡터 생성
            float[] embedding = embeddingApiClient.generateEmbedding(productText);
            
            // 상품에 임베딩 벡터 저장
            product.setDescriptionVector(embedding);
            productRepository.save(product);
            
            log.debug("상품 임베딩 생성 완료: productId={}, textLength={}, vectorSize={}", 
                     product.getNumber(), productText.length(), embedding.length);
            
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
        StringBuilder text = new StringBuilder();
        
        // 상품명
        if (product.getName() != null) {
            text.append(product.getName()).append(" ");
        }
        
        // 카테고리
        if (product.getCategory() != null) {
            text.append(product.getCategory().name()).append(" ");
        }
        
        // 상품 설명
        if (product.getDescription() != null) {
            text.append(product.getDescription()).append(" ");
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
}