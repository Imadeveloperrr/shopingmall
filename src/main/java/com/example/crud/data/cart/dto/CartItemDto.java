package com.example.crud.data.cart.dto;

import com.example.crud.entity.CartItem;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private Long productId;
    private String productName;
    private String productSize;
    private String productColor;
    private Integer price;
    private Integer quantity;
    private String imageUrl;

    /**
     * CartItem Entity → CartItemDto 변환
     *
     * @param cartItem 장바구니 아이템 엔티티
     * @return CartItemDto
     */
    public static CartItemDto from(CartItem cartItem) {
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
}
