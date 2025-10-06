package com.example.crud.ai.recommendation.presentation;

import com.example.crud.ai.embedding.application.EmbeddingService;
import com.example.crud.ai.embedding.application.ProductEmbeddingCommandService;
import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService.ProductSimilarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private final ProductEmbeddingCommandService productEmbeddingCommandService;
    private final EmbeddingService productEmbeddingService;

    /**
     * 텍스트 기반 상품 추천 테스트
     */
    @PostMapping("/text")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> recommendByText(
            @RequestParam(required = false) Long userId,
            @RequestBody Map<String, String> request) {
        long startTime = System.currentTimeMillis();

        String query = request.get("query");
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body(
                    Map.of("error", "query는 필수입니다")
            ));
        }

        log.info("텍스트 기반 추천 테스트: userId={}, query={}", userId, query);

        // 1. 벡터 기반 유사 상품 검색
        CompletableFuture<List<ProductSimilarity>> vectorResultsFuture = vectorService.findSimilarProducts(query, 5);

        // 2. 추천 엔진 테스트 (ProductMatch 형태로)
        CompletableFuture<List<ProductMatch>> recommendationMatchesFuture = recommendationEngine.getRecommendations(query, 5);

        return CompletableFuture.allOf(vectorResultsFuture, recommendationMatchesFuture)
                .thenApply(v -> {

                    List<ProductMatch> recommendationMatches = recommendationMatchesFuture.join();
                    List<ProductSimilarity> vectorResults = vectorResultsFuture.join();

                    long endTime = System.currentTimeMillis();
                    long processingTime = endTime - startTime;

                    Map<String, Object> response = Map.of(
                            "query", query,
                            "vectorResults", vectorResults,
                            "vectorResultCount", vectorResults.size(),
                            "recommendationMatches", recommendationMatches,
                            "recommendationCount", recommendationMatches.size(),
                            "processingTimeMs", processingTime,
                            "processingTimeSec", String.format("%.2f", processingTime / 1000.0)
                    );

                    return ResponseEntity.ok(response);
                })
                .exceptionally(e -> {
                    log.error("추천 테스트 실패", e);
                    return ResponseEntity.status(500).body(
                            Map.of("error", "추천 시스템 오류: " + e.getMessage())
                    );
                });
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
     * 누락된 상품 임베딩 생성 (동기 처리)
     */
    @PostMapping("/generate-embeddings-sync")
    public ResponseEntity<Map<String, Object>> generateEmbeddingsSync() {
        try {
            log.info("=== [동기 순차 처리] 임베딩 생성 시작 ===");

            long startTime = System.currentTimeMillis();
            int count = productEmbeddingService.createMissingEmbeddings();
            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;

            Map<String, Object> response = Map.of(
                "status", "success",
                "method", "SYNC (순차 처리)",
                "processedCount", count,
                "processingTimeMs", processingTime,
                "processingTimeSec", String.format("%.2f", processingTime / 1000.0),
                "timestamp", System.currentTimeMillis()
            );

            log.info("=== [동기 순차 처리] 완료: {}개, {}ms ===", count, processingTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[동기 순차 처리] 임베딩 생성 실패", e);
            return ResponseEntity.status(500).body(
                Map.of(
                    "status", "error",
                    "method", "SYNC",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }

    /**
     * 누락된 상품 임베딩 생성 (비동기 병렬 처리)
     */
    @PostMapping("/generate-embeddings-async")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateEmbeddingsAsync() {
        log.info("=== [비동기 병렬 처리] 임베딩 생성 시작 ===");
        long startTime = System.currentTimeMillis();

        return productEmbeddingService.createMissingEmbeddingsAsync()
                .thenApply(count -> {
                    long processingTime = System.currentTimeMillis() - startTime;

                    Map<String, Object> response = Map.of(
                        "status", "success",
                        "method", "ASYNC (비동기 병렬)",
                        "processedCount", count,
                        "processingTimeMs", processingTime,
                        "processingTimeSec", String.format("%.2f", processingTime / 1000.0),
                        "timestamp", System.currentTimeMillis()
                    );

                    log.info("=== [비동기 병렬 처리] 완료: {}개, {}ms ===", count, processingTime);
                    return ResponseEntity.ok(response);
                })
                .exceptionally(e -> {
                    log.error("[비동기 병렬 처리] 임베딩 생성 실패", e);
                    return ResponseEntity.status(500).body(
                        Map.of(
                            "status", "error",
                            "method", "ASYNC",
                            "message", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                        )
                    );
                });
    }

    /**
     * 특정 상품의 임베딩 재생성
     */
    @PostMapping("/regenerate-embedding/{productId}")
    public ResponseEntity<Map<String, Object>> regenerateProductEmbedding(@PathVariable Long productId) {
        try {
            log.info("상품 임베딩 재생성: productId={}", productId);

            long startTime = System.currentTimeMillis();
            productEmbeddingCommandService.createAndSaveEmbedding(productId);
            long endTime = System.currentTimeMillis();

            Map<String, Object> response = Map.of(
                "status", "success",
                "productId", productId,
                "message", "상품 임베딩 재생성 완료",
                "processingTimeMs", endTime - startTime,
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("상품 임베딩 재생성 실패: productId={}", productId, e);
            return ResponseEntity.status(500).body(
                Map.of(
                    "status", "error",
                    "productId", productId,
                    "message", "임베딩 재생성 실패: " + e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }

    /**
     * 실제 대화 플로우로 추천 테스트 (ConversationalRecommendationService 사용)
     */
    @PostMapping("/conversation-test")
    public ResponseEntity<Map<String, Object>> testConversationalFlow(@RequestBody Map<String, String> request) {
        try {
            String message = request.get("message");
            log.info("🔥 ULTRATHINK - 실제 대화 플로우 테스트: message='{}'", message);

            // 가짜 conversationId로 테스트 (실제로는 DB에 저장되지 않음)
            // conversationalService.processUserMessage(999L, message);

            // 대신 RecommendationEngine을 직접 호출해서 동적 임계값 테스트
            var recommendations = recommendationEngine.getRecommendations(message, 5);

            Map<String, Object> response = Map.of(
                "message", message,
                "recommendations", recommendations,
                "timestamp", System.currentTimeMillis(),
                "flowType", "conversational"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("대화 플로우 테스트 실패", e);
            return ResponseEntity.status(500).body(
                Map.of(
                    "status", "error",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
                )
            );
        }
    }
}
