package com.example.crud.data.product.exception;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;

/**
 * 상품을 찾을 수 없을 때 발생하는 예외
 */
public class ProductNotFoundException extends BaseException {

    public ProductNotFoundException(Long productId) {
        super(ErrorCode.PRODUCT_NOT_FOUND, productId);
    }
}
