package com.example.crud.common.validator;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.ProductOption;
import com.example.crud.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 검증 및 차감 공통 로직
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockValidator {

    private final ProductOptionRepository productOptionRepository;

    /**
     * 재고 충분 여부 검증
     *
     * @param productOption 상품 옵션
     * @param quantity 요청 수량
     * @throws BaseException 재고 부족 시
     */
    public void validateStock(ProductOption productOption, int quantity) {
        if (productOption.getStock() < quantity) {
            log.warn("재고 부족: productOption={}, 현재재고={}, 요청수량={}",
                    productOption.getId(), productOption.getStock(), quantity);
            throw new BaseException(
                    ErrorCode.ORDER_INSUFFICIENT_STOCK,
                    productOption.getStock(),
                    quantity
            );
        }
    }

    /**
     * 재고 차감 (동시성 안전)
     * Native Query로 원자적 업데이트
     *
     * @param productOptionId 상품 옵션 ID
     * @param quantity 차감할 수량
     * @throws BaseException 재고 부족 시
     */
    @Transactional
    public void decreaseStock(Long productOptionId, int quantity) {
        validateStockUpdate(
                productOptionRepository.decreaseStock(productOptionId, quantity),
                ErrorCode.ORDER_INSUFFICIENT_STOCK
        );
    }

    /**
     * 재고 복구 (주문 취소 시)
     *
     * @param productOptionId 상품 옵션 ID
     * @param quantity 복구할 수량
     */
    @Transactional
    public void increaseStock(Long productOptionId, int quantity) {
        validateStockUpdate(
                productOptionRepository.increaseStock(productOptionId, quantity),
                ErrorCode.ORDER_STOCK_RESTORE_FAILED
        );
    }

    /**
     * 재고 업데이트 결과 검증
     * - 로깅은 LoggingAspect에서 자동 처리 (AOP)
     */
    private void validateStockUpdate(int updatedRows, ErrorCode errorCode) {
        if (updatedRows == 0) {
            throw new BaseException(errorCode);
        }
    }
}