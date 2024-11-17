package com.example.crud.data.order.dto;

import com.example.crud.enums.OrderType;
import com.example.crud.enums.PaymentMethodType;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private OrderType orderType;                // CART 또는 DIRECT
    private DirectOrderInfo directOrderInfo;    // 직접 주문시 사용
    private String receiverName;
    private String receiverPhone;
    private String receiverMobile;
    private String receiverAddress;
    private String deliveryMethod;
    private String deliveryMemo;
    private List<Long> cartItemIds;            // 주문할 장바구니 아이템 ID 목록
    private List<OrderItemDto> orderItems;      // 주문 상품 목록
    private Integer totalAmount;                // 총 상품 금액
    private Integer deliveryFee;                // 배송비
    private PaymentMethodType paymentMethod;    // 결제 방법
}