package com.example.crud.data.payment.service.impl;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.dto.PaymentGatewayResponse;
import com.example.crud.data.payment.service.PaymentGatewayClient;
import org.springframework.stereotype.Service;

@Service
public class TossPaymentsClient implements PaymentGatewayClient {

    @Override
    public PaymentGatewayResponse processPayment(PaymentDto paymentDto) {
        // TODO: 토스페이먼츠 API 연동
        // 현재는 임시로 성공 응답 반환
        // 나중에 실제 API 키만 설정하면 됨
        PaymentGatewayResponse response = new PaymentGatewayResponse();
        response.setSuccess(true);
        response.setTransactionId("TOSS_TXN_" + System.currentTimeMillis());
        response.setErrorMessage(null);
        return response;
    }
}
