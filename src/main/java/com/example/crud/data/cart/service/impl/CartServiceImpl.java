package com.example.crud.data.cart.service.impl;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.entity.*;
import com.example.crud.mapper.CartMapper;
import com.example.crud.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final CartMapper cartMapper;

    private Long getAuthenticatedMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("로그인이 필요합니다");
        }
        String userEmail = authentication.getName();
        Member member = memberRepository.findByEmail(userEmail).orElseThrow(() -> new NoSuchElementException("ERROR : 존재하지 않는 사용자입니다."));
        return member.getNumber();
    }

    @Override
    public CartDto getCartByAuthenticateMember() {
        Long memberId = getAuthenticatedMemberId();
        CartDto cartDto = cartMapper.findCartByMemberId(memberId);

        if (cartDto == null) {
            createCart(memberId);
            cartDto = new CartDto();
            cartDto.setId(null);
            cartDto.setCartItems(new ArrayList<>());
            cartDto.setTotalPrice(0);
        } else {
            int totalPrice = cartDto.getCartItems().stream()
                    .mapToInt(item -> item.getPrice() * item.getQuantity())
                    .sum();
            cartDto.setTotalPrice(totalPrice);
        }
        return cartDto;
    }

    @Override
    @Transactional
    public void addCartItem(Long productId, String size, int quantity) {
        Long memberId = getAuthenticatedMemberId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseGet(() -> createCart(memberId));

        ProductSize productSize = productSizeRepository.findByProduct_NumberAndSize(productId, size)
                .orElseThrow(() -> new IllegalArgumentException("해당 사이즈의 상품을 찾을 수 없습니다."));

        // 동일한 상품과 사이즈가 이미 있는지 확인
        CartItem existingCartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().equals(product) && item.getProductSize().equals(productSize))
                .findFirst()
                .orElse(null);

        if (existingCartItem != null) { // 기존 상품의 수량만 증가
            existingCartItem.setQuantity(existingCartItem.getQuantity() + quantity);
        } else { // 새로운 상품 추가
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
    @Transactional
    public void removeCartItem(Long cartItemId) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
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
    public void updateCartItemQuantity(Long cartItemId, int quantity) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));

        // 현재 수량에 변경값을 더합니다
        int newQuantity = cartItem.getQuantity() + quantity;

        // 새로운 수량이 0보다 작으면 에러
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("상품 수량은 1개 이상이어야 합니다.");
        }

        cartItem.setQuantity(newQuantity);
        cartRepository.save(cart);
    }

    @Override
    public void clearCart() {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));
        cart.getCartItems().clear();
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

    // ------------------------------- 24-11-18 추가 -------------------------------

    @Override
    @Transactional
    public void removeOrderedItems(List<Long> cartItemIds) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));

        List<CartItem> itemsToRemove = cart.getCartItems().stream()
                .filter(item -> cartItemIds.contains(item.getId()))
                .collect(Collectors.toList());

        itemsToRemove.forEach(cart::removeCartItem);
        cartRepository.save(cart);
    }

    @Override
    public CartItem getCartItem(Long cartItemId) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new IllegalArgumentException("장바구니를 찾을 수 없습니다."));

        return cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
    }

    @Override
    public CartDto getCartItems(List<Long> cartItemIds) {
        CartDto fullCart = getCartByAuthenticateMember();

        List<CartItemDto> selectedItems = fullCart.getCartItems().stream()
                .filter(item -> cartItemIds.contains(item.getId()))
                .collect(Collectors.toList());

        return CartDto.builder()
                .id(fullCart.getId())
                .cartItems(selectedItems)
                .totalPrice(selectedItems.stream()
                        .mapToInt(item -> item.getPrice() * item.getQuantity())
                        .sum())
                .build();
    }
}
