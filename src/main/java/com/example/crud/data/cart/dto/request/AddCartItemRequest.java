package com.example.crud.data.cart.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 상품 추가 요청
 */
public record AddCartItemRequest(
    @NotNull(message = "상품 ID는 필수입니다.")
    Long productId,

    @NotBlank(message = "색상은 필수입니다.")
    String color,

    @NotBlank(message = "사이즈는 필수입니다.")
    String size,

    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    Integer quantity
) {}
