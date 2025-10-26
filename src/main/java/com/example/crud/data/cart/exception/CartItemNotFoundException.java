package com.example.crud.data.cart.exception;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;

/**
 * 장바구니 아이템을 찾을 수 없을 때 발생하는 예외
 */
public class CartItemNotFoundException extends BaseException {

    public CartItemNotFoundException(Long cartItemId) {
        super(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId);
    }
}
