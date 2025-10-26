package com.example.crud.data.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 장바구니 아이템 수량 변경 요청 DTO
 * quantity: 증가/감소 값 (예: +1, -1)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemQuantityRequest {

    @NotNull(message = "수량 변경값은 필수입니다.")
    private Integer quantity;
}
