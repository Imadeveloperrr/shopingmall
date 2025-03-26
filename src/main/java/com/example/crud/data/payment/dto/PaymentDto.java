package com.example.crud.data.payment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDto {
    private Long orderId;           // 주문 번호
    private int amount;             // 결제 금액
    private String paymentMethod;   // 결제 수단 (예: CREDIT_CARD, BANK_TRANSFER 등)
    private String transactionId;   // 결제 게이트웨이에서 반환하는 거래 ID (결제 후 채워짐)

}
