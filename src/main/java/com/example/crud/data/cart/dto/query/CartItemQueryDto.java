package com.example.crud.data.cart.dto.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MyBatis 전용 장바구니 아이템 조회 모델.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemQueryDto {

    private Long id;
    private Long productId;
    private String productName;
    private String productSize;
    private String productColor;
    private Integer price;
    private Integer quantity;
    private String imageUrl;
}
