package com.example.crud.data.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartResponse(
    Long id,
    List<CartItemResponse> cartItems,
    Integer totalPrice
) {
    /**
     * 빈 장바구니 생성
     */
    public static CartResponse empty() {
        return new CartResponse(null, List.of(), 0);
    }

    /**
     * 총 가격 계산
     */
    public static int calculateTotalPrice(List<CartItemResponse> items) {
        return items.stream()
            .mapToInt(item -> item.price() * item.quantity())
            .sum();
    }
}
