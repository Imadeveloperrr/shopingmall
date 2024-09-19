package com.example.crud.data.cart.service.impl;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.entity.*;
import com.example.crud.repository.CartItemRepository;
import com.example.crud.repository.CartRepository;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    public CartDto getCartByMemberId(Long memberId) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니가 존재하지 않습니다."));
        return convertToCartDto(cart);
    }

    @Override
    public void addCartItem(Long memberId, Long productId, String size, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> createCart(memberId));

        ProductSize productSize = product.getProductSizeBySize(size);

        // 동일한 상품과 사이즈가 이미 있는지 확인
        CartItem existingCartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().equals(product) && item.getProductSize().equals(productSize))
                .findFirst()
                .orElse(null);

        if (existingCartItem != null) { // 기존 상품의 수량만 증가
            existingCartItem.setQuantity(existingCartItem.getQuantity() + quantity);
        } else { // 새로운 상품 추가.
            CartItem newCartItem = CartItem.builder()
                    .productSize(productSize)
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.addCartItem(newCartItem);
        }

        cartRepository.save(cart);
    }

    @Override
    public Cart createCart(Long memberId) {
        Cart cart = Cart.builder()
                .member(Member.builder().number(memberId).build())
                .cartItems(new ArrayList<>())
                .build();
        return cartRepository.save(cart);
    }

    @Override
    public void removeCartItem(Long memberId, Long cartItemId) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));

        cart.removeCartItem(cartItem);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long memberId, Long cartItemId, int quantity) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));

        cartItem.setQuantity(quantity);
        cartRepository.save(cart);
    }

    @Override
    public void clearCart(Long memberId) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));
        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    @Override
    public CartDto convertToCartDto(Cart cart) {
        // CartItemDTO
        List<CartItemDto> cartItemDtos = cart.getCartItems().stream().map(cartItem ->
                CartItemDto.builder()
                        .id(cartItem.getId())
                        .productName(cartItem.getProduct().getName())
                        .productSize(cartItem.getProductSize().getSize())
                        .price(cartItem.getProduct().getPrice())
                        .quantity(cartItem.getQuantity())
                        .imageUrl(cartItem.getProduct().getImageUrl())
                        .build()
        ).collect(Collectors.toList());
        return CartDto.builder()
                .id(cart.getId())
                .cartItems(cartItemDtos)
                .totalPrice(cartItemDtos.stream()
                        .mapToInt(item -> item.getPrice() * item.getQuantity())
                        .sum())
                .build();
    }
}
