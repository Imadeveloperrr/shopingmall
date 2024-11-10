package com.example.crud.data.order.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private String receiverName;
    private String receiverPhone;
    private String receiverMobile;
    private String receiverAddress;
    private String deliveryMethod;
    private String deliveryMemo;
    private List<Long> cartItemIds; // 주문할 장바구니 아이템 ID 목록
}