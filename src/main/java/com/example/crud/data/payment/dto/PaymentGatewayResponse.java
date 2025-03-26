package com.example.crud.data.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {
    private boolean success;        // 결제 성공 여부
    private String transactionId;   // 거래 ID (성공 시)
    private String errorMessage;    // 실패 시 에러 메시지
}
