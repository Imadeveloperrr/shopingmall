package com.example.crud.common.utility.validator;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.ProductOption;
import com.example.crud.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
     * @param quantity      요청 수량
     * @thorws BaseException 재고 부족 시
     */
    public void validateStock(ProductOption productOption, int quantity) {
        if (productOption.getStock() < quantity) {
            log.warn("재고 부족: productOption={}, 현재재고={}, 요청수량={}", productOption.getId(), productOption.getStock(), quantity);
            throw new BaseException(
                    ErrorCode.ORDER_INSUFFICIENT_STOCK,
                    productOption.getStock(),
                    quantity
            );
        }
    }

}
