package com.example.crud.data.cart.converter;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 장바구니 Entity <-> DTO 변환
 * - MyBatis는 복잡한 조인 쿼리에 활용
 * - Converter는 단순 Entity-DTO 변환에만 활용
 */
@Component
public class CartConverter {

    /**
     * Cart Entity -> CartDto 변환
     *
     * @param cart 장바구니 엔티티
     * @return CartDto
     */
    public CartDto toDto(Cart cart) {
        if (cart == null) {
            return createEmptyCartDto();
        }

        List<CartItemDto> cartItemDtos = cart.getCartItems() != null
                ? cart.getCartItems().stream()
                    .map(this::toCartItemDto)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return CartDto.builder()
                .id(cart.getId())
                .cartItems(cartItemDtos)
                .totalPrice(calculateTotalPrice(cartItemDtos))
                .build();
    }

    /**
     * CartItem Entity -> CartItemDto 변환
     *
     * @param cartItem 장바구니 아이템 엔티티
     * @return CartItemDto
     */
    public CartItemDto toCartItemDto(CartItem cartItem) {
        return CartItemDto.builder()
                .id(cartItem.getId())
                .productId(cartItem.getProduct().getNumber())
                .productName(cartItem.getProduct().getName())
                .productSize(cartItem.getProductOption().getSize())
                .productColor(cartItem.getProductOption().getColor())
                .price(cartItem.getProduct().getPrice())
                .quantity(cartItem.getQuantity())
                .imageUrl(cartItem.getProduct().getImageUrl())
                .build();
    }

    /**
     * 선택된 CartItem들만 변환
     *
     * @param cart 장바구니
     * @param cartItemIds 선택된 아이템 ID 목록
     * @return CartDto
     */
    public CartDto toDtoWithSelectedItems(Cart cart, List<Long> cartItemIds) {
        if (cart == null || cartItemIds == null || cartItemIds.isEmpty()) {
            return createEmptyCartDto();
        }

        List<CartItemDto> selectedItems = cart.getCartItems().stream()
                .filter(item -> cartItemIds.contains(item.getId()))
                .map(this::toCartItemDto)
                .collect(Collectors.toList());

        return CartDto.builder()
                .id(cart.getId())
                .cartItems(selectedItems)
                .totalPrice(calculateTotalPrice(selectedItems))
                .build();
    }

    /**
     * 빈 장바구니 DTO 생성
     *
     * @return 빈 CartDto
     */
    private CartDto createEmptyCartDto() {
        return CartDto.builder()
                .id(null)
                .cartItems(Collections.emptyList())
                .totalPrice(0)
                .build();
    }

    /**
     * 총 가격 계산
     *
     * @param cartItems 장바구니 아이템 목록
     * @return 총 가격
     */
    private int calculateTotalPrice(List<CartItemDto> cartItems) {
        return cartItems.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }
}
