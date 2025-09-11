package com.example.crud.ai.recommendation.presentation;

import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService.ProductSimilarity;
import com.example.crud.ai.embedding.EmbeddingApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 추천 시스템 테스트용 컨트롤러
 */
@RestController
@RequestMapping("/api/test/recommendation")
@RequiredArgsConstructor
@Slf4j
public class RecommendationTestController {

    private final RecommendationEngine recommendationEngine;
    private final ProductVectorService vectorService;
    private final EmbeddingApiClient embeddingApiClient;

    /**
     * 텍스트 기반 상품 추천 테스트
     */
    @PostMapping("/text")
    public ResponseEntity<Map<String, Object>> recommendByText(
            @RequestParam(required = false) Long userId,
            @RequestBody Map<String, String> request) {
        
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "query는 필수입니다")
                );
            }

            log.info("텍스트 기반 추천 테스트: userId={}, query={}", userId, query);

            // 1. 벡터 기반 유사 상품 검색
            List<ProductSimilarity> vectorResults = vectorService.findSimilarProducts(query, 10);
            
            // 2. 추천 엔진 테스트 (ProductMatch 형태로)
            var recommendationMatches = recommendationEngine.getRecommendations(query, 10);

            Map<String, Object> response = Map.of(
                "query", query,
                "vectorResults", vectorResults,
                "vectorResultCount", vectorResults.size(),
                "recommendationMatches", recommendationMatches,
                "recommendationCount", recommendationMatches.size(),
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("추천 테스트 실패", e);
            return ResponseEntity.status(500).body(
                Map.of("error", "추천 시스템 오류: " + e.getMessage())
            );
        }
    }

    /**
     * 임베딩 생성 테스트
     */
    @PostMapping("/embedding")
    public ResponseEntity<Map<String, Object>> testEmbedding(
            @RequestBody Map<String, String> request) {
        
        try {
            String text = request.get("text");
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "text는 필수입니다")
                );
            }

            long startTime = System.currentTimeMillis();
            float[] embedding = embeddingApiClient.generateEmbedding(text);
            long endTime = System.currentTimeMillis();

            Map<String, Object> response = Map.of(
                "text", text,
                "embeddingDimension", embedding.length,
                "processingTimeMs", endTime - startTime,
                "embeddingPreview", List.of(
                    embedding[0], embedding[1], embedding[2], 
                    embedding[3], embedding[4]
                ),
                "embeddingNorm", calculateNorm(embedding),
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("임베딩 테스트 실패", e);
            return ResponseEntity.status(500).body(
                Map.of("error", "임베딩 생성 오류: " + e.getMessage())
            );
        }
    }

    /**
     * 상품간 유사도 테스트
     */
    @GetMapping("/similarity/{productId}")
    public ResponseEntity<Map<String, Object>> testProductSimilarity(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "5") int limit) {
        
        try {
            log.info("상품 유사도 테스트: productId={}, limit={}", productId, limit);

            List<ProductSimilarity> similarities = vectorService.findSimilarProductsByProduct(productId, limit);

            Map<String, Object> response = Map.of(
                "targetProductId", productId,
                "similarProducts", similarities,
                "similarProductCount", similarities.size(),
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("유사도 테스트 실패", e);
            return ResponseEntity.status(500).body(
                Map.of("error", "유사도 계산 오류: " + e.getMessage())
            );
        }
    }

    /**
     * 시스템 상태 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            // 간단한 임베딩 테스트
            long startTime = System.currentTimeMillis();
            float[] testEmbedding = embeddingApiClient.generateEmbedding("테스트");
            long endTime = System.currentTimeMillis();

            Map<String, Object> response = Map.of(
                "status", "healthy",
                "embeddingApiClient", "working",
                "embeddingDimension", testEmbedding.length,
                "responseTimeMs", endTime - startTime,
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("헬스 체크 실패", e);
            return ResponseEntity.status(500).body(
                Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }

    /**
     * 벡터 노름 계산
     */
    private double calculateNorm(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}