package com.example.crud.data.cart.service.impl;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.*;
import com.example.crud.common.mapper.CartMapper;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;
    private final ProductOptionRepository productOptionRepository;

    private Long getAuthenticatedMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }
        String userEmail = authentication.getName();
        Member member = memberRepository.findByEmail(userEmail).orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
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
    public void addCartItem(Long productId, String color, String size, int quantity) {
        Long memberId = getAuthenticatedMemberId();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND, productId));

        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseGet(() -> createCart(memberId));

        ProductOption productOption = productOptionRepository
                .findByProduct_NumberAndColorAndSize(productId, color, size)
                .orElseThrow(() -> new BaseException(ErrorCode.CART_OPTION_NOT_FOUND,
                                productId,
                                color,
                                size));

        // 재고 확인
        if (productOption.getStock() < quantity) {
            throw new BaseException(ErrorCode.CART_INSUFFICIENT_STOCK, productOption.getStock(), quantity);
        }

        // 동일한 상품과 옵션이 이미 있는지 확인
        CartItem existingCartItem = cart.getCartItems().stream()
                .filter(item -> item.getProduct().equals(product)
                        && item.getProductOption().equals(productOption))
                .findFirst()
                .orElse(null);

        if (existingCartItem != null) {
            existingCartItem.setQuantity(existingCartItem.getQuantity() + quantity);
        } else {
            CartItem newCartItem = CartItem.builder()
                    .productOption(productOption)
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
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId));

        cart.removeCartItem(cartItem);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateCartItemQuantity(Long cartItemId, int quantity) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId));

        // 현재 수량에 변경값을 더합니다
        int newQuantity = cartItem.getQuantity() + quantity;

        // 새로운 수량이 0보다 작으면 에러
        if (newQuantity <= 0) {
            throw new BaseException(ErrorCode.CART_INSUFFICIENT_QUANTITY, newQuantity);
        }

        cartItem.setQuantity(newQuantity);
        cartRepository.save(cart);
    }

    @Override
    public void clearCart() {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));
        cart.getCartItems().clear();
    }

    @Override
    public CartDto convertToCartDto(Cart cart) {
        List<CartItemDto> cartItemDtos = cart.getCartItems().stream().map(cartItem ->
                CartItemDto.builder()
                        .id(cartItem.getId())
                        .productId(cartItem.getProduct().getNumber())
                        .productName(cartItem.getProduct().getName())
                        .productSize(cartItem.getProductOption().getSize())
                        .productColor(cartItem.getProductOption().getColor())
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
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));

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
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));

        return cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId));
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

    // ------------------------------- 24-11-28 추가 -------------------------------
    @Override
    @Transactional
    public void updateCartItemOption(Long cartItemId, String newColor, String newSize) {
        Long memberId = getAuthenticatedMemberId();
        Cart cart = cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new BaseException(ErrorCode.CART_NOT_FOUND, memberId));

        CartItem cartItem = cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId));

        ProductOption newOption = productOptionRepository
                .findByProduct_NumberAndColorAndSize(
                        cartItem.getProduct().getNumber(),
                        newColor,
                        newSize
                )
                .orElseThrow(() -> new BaseException(ErrorCode.CART_OPTION_NOT_FOUND,
                        cartItem.getProduct().getNumber(),
                        newColor,
                        newSize));

        // 재고 확인
        if (newOption.getStock() < cartItem.getQuantity()) {
            throw new BaseException(ErrorCode.CART_INSUFFICIENT_STOCK,
                            newOption.getStock(),
                            cartItem.getQuantity());
        }

        cartItem.setProductOption(newOption);
        cartRepository.save(cart);
    }

}
