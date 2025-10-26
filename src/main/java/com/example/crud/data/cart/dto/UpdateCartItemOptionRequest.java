package com.example.crud.data.cart.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 장바구니 아이템 옵션 변경 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemOptionRequest {

    @NotBlank(message = "색상은 필수입니다.")
    private String color;

    @NotBlank(message = "사이즈는 필수입니다.")
    private String size;
}
