package com.example.crud.data.cart.service.find;

import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.cart.dto.response.CartResponse;
import com.example.crud.entity.CartItem;
import java.util.List;

/**
 * 장바구니 조회 서비스.
 */
public interface CartFindService {

    /**
     * 인증된 회원 장바구니 조회 (뷰 용도).
     */
    CartResponse getCart();

    /**
     * 선택된 아이템만 조회 (뷰 용도).
     */
    CartResponse getSelectedItems(List<Long> cartItemIds);

    /**
     * 주문용 DTO 조회.
     */
    List<CartCheckoutItem> getCheckoutItems(List<Long> cartItemIds);

    /**
     * 단일 아이템 조회 (소유자 검증 포함).
     */
    CartItem getCartItem(Long cartItemId);
}
