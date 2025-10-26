package com.example.crud.data.cart.dto;

import com.example.crud.entity.Cart;
import lombok.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartDto {
    private Long id;
    private List<CartItemDto> cartItems;
    private Integer totalPrice;

    public Integer calculateTotalPrice() {
        return cartItems.stream().mapToInt(item -> item.getPrice() * item.getQuantity()).sum();
    }

    /**
     * Cart Entity → CartDto 변환
     *
     * @param cart 장바구니 엔티티
     * @return CartDto
     */
    public static CartDto from(Cart cart) {
        if (cart == null) {
            return createEmpty();
        }

        List<CartItemDto> cartItemDtos = cart.getCartItems() != null
                ? cart.getCartItems().stream()
                    .map(CartItemDto::from)
                    .collect(Collectors.toList())
                : Collections.emptyList();

        return CartDto.builder()
                .id(cart.getId())
                .cartItems(cartItemDtos)
                .totalPrice(calculateTotalPrice(cartItemDtos))
                .build();
    }

    /**
     * 선택된 CartItem들만 변환
     *
     * @param cart 장바구니
     * @param cartItemIds 선택된 아이템 ID 목록
     * @return CartDto
     */
    public static CartDto fromSelectedItems(Cart cart, List<Long> cartItemIds) {
        if (cart == null || cartItemIds == null || cartItemIds.isEmpty()) {
            return createEmpty();
        }

        List<CartItemDto> selectedItems = cart.getCartItems().stream()
                .filter(item -> cartItemIds.contains(item.getId()))
                .map(CartItemDto::from)
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
    public static CartDto createEmpty() {
        return CartDto.builder()
                .id(null)
                .cartItems(Collections.emptyList())
                .totalPrice(0)
                .build();
    }

    /**
     * 총 가격 계산 (정적 메서드)
     *
     * @param cartItems 장바구니 아이템 목록
     * @return 총 가격
     */
    private static int calculateTotalPrice(List<CartItemDto> cartItems) {
        return cartItems.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }
}
