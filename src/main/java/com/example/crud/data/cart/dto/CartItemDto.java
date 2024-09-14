package com.example.crud.data.cart.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long id;
    private String productName;
    private String productSize;
    private Integer price;
    private Integer quantity;
    private String imageUrl;
}
