package com.example.crud.data.cart.service;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.entity.Cart;

public interface CartService {
    // 장바구니 조회
    CartDto getCartByAuthenticateMember();
    // 장바구니 생성
    Cart createCart(Long memberId);
    // 장바구니 상품 추가
    void addCartItem(Long productId, String size, int quantity);
    //상품 제거
    void removeCartItem(Long cartItemId);
    // 장바구니 비우기
    void clearCart();
    // 상품 수량 변경
    void updateCartItemQuantity(Long cartItemId, int quantity);
    // Cart -> CartDTO 변환
    CartDto convertToCartDto(Cart cart);
}