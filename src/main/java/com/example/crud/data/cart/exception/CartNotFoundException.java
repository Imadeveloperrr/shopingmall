package com.example.crud.data.cart.exception;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;

/**
 * 장바구니를 찾을 수 없을 때 발생하는 예외
 */
public class CartNotFoundException extends BaseException {

    public CartNotFoundException(Long memberId) {
        super(ErrorCode.CART_NOT_FOUND, memberId);
    }
}
