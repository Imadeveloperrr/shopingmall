package com.example.crud.data.cart.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 아이템 수량 변경 요청
 * quantity: 증가/감소 값 (예: +1, -1)
 */
public record UpdateCartItemQuantityRequest(
    @NotNull(message = "수량 변경값은 필수입니다.")
    Integer quantity
) {}
