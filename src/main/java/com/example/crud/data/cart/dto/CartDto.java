package com.example.crud.data.cart.dto;

import lombok.*;

import java.util.List;

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
}
