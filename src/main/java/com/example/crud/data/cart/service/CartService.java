package com.example.crud.data.cart.service;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.entity.Cart;

public interface CartService {
    // 장바구니 조회
    CartDto getCartByMemberId(Long memberId);
    // 장바구니 생성
    Cart createCart(Long memberId);
    // 장바구니 상품 추가
    void addCartItem(Long memberId, Long productId, String size, int quantity);
    //상품 제거
    void removeCartItem(Long memberId, Long cartItemId);
    // 장바구니 비우기
    void clearCart(Long memberId);
    // Cart -> CartDTO 변환
    CartDto convertToCartDto(Cart cart);
}
