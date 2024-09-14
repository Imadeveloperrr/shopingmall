package com.example.crud.data.cart.service.impl;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.entity.Cart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartServiceImpl implements CartService {
    @Override
    public CartDto getCartByMemberId(Long memberId) {
        return null;
    }

    @Override
    public Cart createCart(Long memberId) {
        return null;
    }

    @Override
    public void addCartItem(Long memberId, Long productId, String size, int quantity) {

    }

    @Override
    public void removeCartItem(Long memberId, Long cartItemId) {

    }

    @Override
    public void clearCart(Long memberId) {

    }

    @Override
    public CartDto convertToCartDto(Cart cart) {
        return null;
    }
}
