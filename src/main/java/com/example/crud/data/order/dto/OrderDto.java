package com.example.crud.data.order.dto;

import com.example.crud.enums.OrderType;
import com.example.crud.enums.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private OrderType orderType;                // CART 또는 DIRECT

    @NotBlank(message = "수취인 이름은 필수입니다.")
    private String receiverName;

    private String receiverPhone;

    @NotBlank(message = "수취인 휴대폰번호는 필수입니다.")
    private String receiverMobile;

    @NotBlank(message = "배송 주소는 필수입니다.")
    private String receiverAddress;

    private String deliveryMethod;

    private String deliveryMemo;

    // 장바구니 주문일 경우 필수
    private List<Long> cartItemIds;            // 주문할 장바구니 아이템 ID 목록

    // 직접 주문일 경우 주문 상품 목록 필수
    private List<OrderItemDto> orderItems;      // 주문 상품 목록

    private Integer totalAmount;                // 총 상품 금액

    private Integer deliveryFee;                // 배송비

    private PaymentMethodType paymentMethod;    // 결제 방법
}