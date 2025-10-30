package com.example.crud.data.cart.dto.query;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MyBatis 전용 장바구니 조회 모델.
 * - resultMap에서 값을 주입하기 위해 기본 생성자와 setter 필요
 * - 인프라 계층 내부에서만 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartQueryDto {

    private Long id;

    /**
     * MyBatis <collection> 매핑 시 null 리스트 방지.
     */
    @Builder.Default
    private List<CartItemQueryDto> cartItems = new ArrayList<>();
}
