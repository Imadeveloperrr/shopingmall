package com.example.crud.data.product.exception;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;

/**
 * 상품 옵션을 찾을 수 없을 때 발생하는 예외
 */
public class ProductOptionNotFoundException extends BaseException {

    public ProductOptionNotFoundException(Long productId, String color, String size) {
        super(ErrorCode.PRODUCT_OPTION_NOT_FOUND, productId, color, size);
    }
}
