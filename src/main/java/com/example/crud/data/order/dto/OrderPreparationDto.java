package com.example.crud.data.order.dto;

import com.example.crud.enums.PaymentMethodType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPreparationDto {
    private List<OrderItemDto> orderItems;          // 주문 상품 목록
    private int totalAmount;                        // 상품 총액
    private int deliveryFee;                        // 배송비
    private int finalAmount;                        // 최종 결제 금액 (상품 총액 + 배송비)
    private String deliveryMethod;                  // 배송 방법
    private PaymentMethodType paymentMethod;        // 결제 방법
    private boolean isDirectOrder;                  // 직접 주문 여부

    public void calculateFinalAmount() {
        this.finalAmount = this.totalAmount + this.deliveryFee;
    }
}
