package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.infrastructure.ProductVectorService;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * 메시지 기반으로 유사한 상품을 조회해 상위 결과를 반환하는 추천 엔진.
 *
 * - 입력 검증: 공백 메시지, limit 1~10 범위 확인.
 * - 처리: ProductVectorService로 유사도 계산 후 ProductMatch로 변환.
 * - 최적화: description 등 장문 필드 제외로 메모리/네트워크 비용 절감.
 * - 예외: 검증/내부 오류는 BaseException으로 전달해 글로벌 핸들러에서 일관 처리.
 *
 *  추가 개선할 사항 -> 캐싱 구현.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationEngine {

    private final ProductVectorService vectorService;

    public CompletableFuture<List<ProductMatch>> getRecommendations(String message, int limit) {
        if (message == null || message.trim().isEmpty()) {
            log.warn("빈 메시지로 추천 생성 요청됨.");
            throw new BaseException(ErrorCode.INVALID_MESSAGE_INPUT);
        }
        if (limit < 1 || limit > 10) {
            log.warn("잘못된 추천 개수 요청: {}", limit);
            throw new BaseException(ErrorCode.INVALID_MESSAGE_INPUT, "추천 개수는 1~10 사이여야 합니다.");
        }

        return vectorService.findSimilarProducts(message, limit)
                .thenApply(vectorMatches ->
                    vectorMatches.stream()
                            .map(s -> new ProductMatch(
                                    s.productId(),
                                    s.productName(),
                                    s.similarity()
                            ))
                            .collect(Collectors.toList())
                )
                .handle((result, ex) -> {
                    if (ex != null) {
                        throw mapRecommendationException(ex, message, limit);
                    }
                    return result;
                });
    }

    private RuntimeException mapRecommendationException(Throwable throwable, String message, int limit) {
        Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        if (cause instanceof BaseException baseException) {
            return baseException;
        }
        log.error("추천 생성 중 오류 발생: message={}, limit={}", message, limit, cause);
        return new BaseException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
