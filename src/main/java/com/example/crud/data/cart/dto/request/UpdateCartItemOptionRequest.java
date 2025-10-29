package com.example.crud.data.cart.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 장바구니 아이템 옵션 변경 요청
 */
public record UpdateCartItemOptionRequest(
    @NotBlank(message = "색상은 필수입니다.")
    String color,

    @NotBlank(message = "사이즈는 필수입니다.")
    String size
) {}
