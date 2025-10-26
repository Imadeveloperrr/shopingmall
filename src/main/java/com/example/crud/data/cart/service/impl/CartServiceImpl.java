package com.example.crud.data.cart.service.impl;

import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.converter.CartConverter;
import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.exception.CartItemNotFoundException;
import com.example.crud.data.cart.exception.CartNotFoundException;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.data.cart.validator.CartValidator;
import com.example.crud.data.product.service.ProductFindService;
import com.example.crud.entity.*;
import com.example.crud.common.mapper.CartMapper;
import com.example.crud.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 장바구니 서비스 구현
 * - 조회: MyBatis CartMapper 활용 (복잡한 조인 쿼리 성능 최적화)
 * - 변환: CartConverter 활용
 * - 검증: CartValidator 활용
 * - 인증: SecurityUtil 활용
 * - 도메인 조회: ProductFindService 활용 (cheese10yun 방식)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartMapper cartMapper;
    private final CartValidator cartValidator;
    private final CartConverter cartConverter;
    private final SecurityUtil securityUtil;
    private final ProductFindService productFindService;

    /**
     * 인증된 회원의 장바구니 조회 (MyBatis 활용)
     *
     * @return 장바구니 DTO
     */
    @Override
    public CartDto getCartByAuthenticateMember() {
        Long memberId = securityUtil.getCurrentMemberId();
        CartDto cartDto = cartMapper.findCartByMemberId(memberId);

        if (cartDto == null) {
            createCart(memberId);
            return cartConverter.toDto(null); // 빈 장바구니 반환
        }

        // MyBatis 조회 결과에 총 가격 설정
        if (cartDto.getTotalPrice() == null) {
            int totalPrice = cartDto.getCartItems().stream()
                    .mapToInt(item -> item.getPrice() * item.getQuantity())
                    .sum();
            cartDto.setTotalPrice(totalPrice);
        }

        return cartDto;
    }

    /**
     * 장바구니에 상품 추가
     *
     * @param productId 상품 ID
     * @param color 색상
     * @param size 크기
     * @param quantity 수량
     */
    @Override
    @Transactional
    public void addCartItem(Long productId, String color, String size, int quantity) {
        // 검증
        cartValidator.validateQuantity(quantity);

        // 엔티티 조회
        Long memberId = securityUtil.getCurrentMemberId();
        Product product = productFindService.getProduct(productId);
        ProductOption productOption = productFindService.getProductOption(productId, color, size);

        // 재고 검증
        cartValidator.validateStock(productOption, quantity);

        // 장바구니 조회 또는 생성
        Cart cart = getOrCreateCart(memberId);

        // 동일 상품/옵션이 이미 있는지 확인
        CartItem existingCartItem = findExistingCartItem(cart, product, productOption);

        if (existingCartItem != null) {
            // 기존 아이템 수량 증가
            int newQuantity = existingCartItem.getQuantity() + quantity;
            cartValidator.validateStock(productOption, newQuantity);
            existingCartItem.setQuantity(newQuantity);
        } else {
            // 새 아이템 추가
            CartItem newCartItem = CartItem.builder()
                    .productOption(productOption)
                    .product(product)
                    .quantity(quantity)
                    .build();
            cart.addCartItem(newCartItem);
        }

        cartRepository.save(cart);
    }

    /**
     * 장바구니 생성
     *
     * @param memberId 회원 ID
     * @return 생성된 장바구니
     */
    @Override
    public Cart createCart(Long memberId) {
        Cart cart = Cart.builder()
                .member(Member.builder().number(memberId).build())
                .cartItems(new ArrayList<>())
                .build();
        return cartRepository.save(cart);
    }

    /**
     * 장바구니 아이템 삭제
     *
     * @param cartItemId 장바구니 아이템 ID
     */
    @Override
    @Transactional
    public void removeCartItem(Long cartItemId) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);

        CartItem cartItem = getCartItemFromCart(cart, cartItemId);
        cart.removeCartItem(cartItem);
        cartRepository.save(cart);
    }

    /**
     * 장바구니 아이템 수량 업데이트
     *
     * @param cartItemId 장바구니 아이템 ID
     * @param quantity 수량 변경값 (증가/감소)
     */
    @Override
    @Transactional
    public void updateCartItemQuantity(Long cartItemId, int quantity) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);

        CartItem cartItem = getCartItemFromCart(cart, cartItemId);

        // 새로운 수량 검증
        cartValidator.validateNewQuantity(cartItem.getQuantity(), quantity);

        int newQuantity = cartItem.getQuantity() + quantity;
        cartItem.setQuantity(newQuantity);
        cartRepository.save(cart);
    }

    /**
     * 장바구니 비우기
     */
    @Override
    @Transactional
    public void clearCart() {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);
        cart.getCartItems().clear();
        cartRepository.save(cart);
    }

    /**
     * Cart Entity -> CartDto 변환 (CartConverter 사용)
     *
     * @param cart 장바구니 엔티티
     * @return CartDto
     */
    @Override
    public CartDto convertToCartDto(Cart cart) {
        return cartConverter.toDto(cart);
    }

    // ------------------------------- 주문 관련 -------------------------------

    /**
     * 주문 완료된 아이템 제거
     *
     * @param cartItemIds 제거할 장바구니 아이템 ID 목록
     */
    @Override
    @Transactional
    public void removeOrderedItems(List<Long> cartItemIds) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);

        List<CartItem> itemsToRemove = cart.getCartItems().stream()
                .filter(item -> cartItemIds.contains(item.getId()))
                .collect(Collectors.toList());

        itemsToRemove.forEach(cart::removeCartItem);
        cartRepository.save(cart);
    }

    /**
     * 장바구니 아이템 조회
     *
     * @param cartItemId 장바구니 아이템 ID
     * @return CartItem
     */
    @Override
    public CartItem getCartItem(Long cartItemId) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);
        return getCartItemFromCart(cart, cartItemId);
    }

    /**
     * 선택된 장바구니 아이템들만 조회
     *
     * @param cartItemIds 조회할 장바구니 아이템 ID 목록
     * @return 선택된 아이템들의 CartDto
     */
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

    /**
     * 장바구니 아이템 옵션 변경 (색상, 사이즈)
     *
     * @param cartItemId 장바구니 아이템 ID
     * @param newColor 새 색상
     * @param newSize 새 사이즈
     */
    @Override
    @Transactional
    public void updateCartItemOption(Long cartItemId, String newColor, String newSize) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = getCart(memberId);

        CartItem cartItem = getCartItemFromCart(cart, cartItemId);

        // 새 옵션 조회
        ProductOption newOption = productFindService.getProductOption(
                cartItem.getProduct().getNumber(),
                newColor,
                newSize
        );

        // 재고 검증
        cartValidator.validateStock(newOption, cartItem.getQuantity());

        cartItem.setProductOption(newOption);
        cartRepository.save(cart);
    }

    // ------------------------------- Private Helper 메서드 -------------------------------

    /**
     * 장바구니 조회 (없으면 생성)
     */
    private Cart getOrCreateCart(Long memberId) {
        return cartRepository.findByMemberNumber(memberId)
                .orElseGet(() -> createCart(memberId));
    }

    /**
     * 장바구니 조회 (없으면 예외)
     */
    private Cart getCart(Long memberId) {
        return cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new CartNotFoundException(memberId));
    }

    /**
     * 장바구니에서 아이템 조회
     */
    private CartItem getCartItemFromCart(Cart cart, Long cartItemId) {
        return cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
    }

    /**
     * 동일 상품/옵션 아이템 찾기
     */
    private CartItem findExistingCartItem(Cart cart, Product product, ProductOption productOption) {
        return cart.getCartItems().stream()
                .filter(item -> item.getProduct().equals(product)
                        && item.getProductOption().equals(productOption))
                .findFirst()
                .orElse(null);
    }
}
