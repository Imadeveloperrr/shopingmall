package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.ai.embedding.SimpleEmbeddingService;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 벡터 기반 상품 매칭 서비스 - 간소화된 버전
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductVectorService {

    private final ProductRepository productRepository;
    private final SimpleEmbeddingService embeddingService;

    /**
     * 쿼리 텍스트와 유사한 상품들을 벡터 기반으로 찾기
     */
    public List<ProductSimilarity> findSimilarProducts(String queryText, int limit) {
        try {
            // 1. 쿼리 텍스트의 임베딩 생성
            float[] queryVector = embeddingService.generateEmbedding(queryText);
            if (queryVector == null) {
                return new ArrayList<>();
            }

            // 2. 모든 상품과 유사도 계산
            List<Product> products = productRepository.findAll();
            List<ProductSimilarity> similarities = new ArrayList<>();

            for (Product product : products) {
                if (product.getDescriptionVector() != null) {
                    double similarity = embeddingService.calculateSimilarity(
                        queryVector, product.getDescriptionVector());
                    
                    if (similarity > 0.3) { // 최소 임계값
                        similarities.add(new ProductSimilarity(
                            product.getNumber(), 
                            similarity,
                            product.getName(),
                            product.getDescription()
                        ));
                    }
                }
            }

            // 3. 유사도 순으로 정렬하여 상위 N개 반환
            return similarities.stream()
                .sorted(Comparator.comparingDouble(ProductSimilarity::similarity).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("벡터 기반 상품 검색 실패: query={}", queryText, e);
            return new ArrayList<>();
        }
    }

    /**
     * 상품의 설명 벡터 생성 및 저장
     */
    @Async
    @Transactional
    public void generateProductVectors(List<Long> productIds) {
        try {
            List<Product> products = productRepository.findAllById(productIds);
            
            for (Product product : products) {
                if (product.getDescriptionVector() == null && product.getDescription() != null) {
                    String description = product.getName() + " " + product.getDescription();
                    float[] vector = embeddingService.generateEmbedding(description);
                    
                    product.setDescriptionVector(vector);
                    productRepository.save(product);
                    
                    log.debug("상품 벡터 생성: productId={}, name={}", 
                        product.getNumber(), product.getName());
                }
            }

        } catch (Exception e) {
            log.error("상품 벡터 생성 실패: productIds={}", productIds, e);
        }
    }

    /**
     * 특정 상품과 유사한 상품들 찾기
     */
    public List<ProductSimilarity> findSimilarProductsByProduct(Long productId, int limit) {
        try {
            Product targetProduct = productRepository.findById(productId).orElse(null);
            if (targetProduct == null || targetProduct.getDescriptionVector() == null) {
                return new ArrayList<>();
            }

            List<Product> allProducts = productRepository.findAll();
            List<ProductSimilarity> similarities = new ArrayList<>();

            for (Product product : allProducts) {
                if (!product.getNumber().equals(productId) && product.getDescriptionVector() != null) {
                    double similarity = embeddingService.calculateSimilarity(
                        targetProduct.getDescriptionVector(), 
                        product.getDescriptionVector());
                        
                    if (similarity > 0.4) {
                        similarities.add(new ProductSimilarity(
                            product.getNumber(),
                            similarity,
                            product.getName(),
                            product.getDescription()
                        ));
                    }
                }
            }

            return similarities.stream()
                .sorted(Comparator.comparingDouble(ProductSimilarity::similarity).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("유사 상품 검색 실패: productId={}", productId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 상품 유사도 결과 클래스
     */
    public record ProductSimilarity(
        Long productId,
        double similarity,
        String productName,
        String description
    ) {}
}