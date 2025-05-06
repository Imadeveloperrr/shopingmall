package com.example.crud.data.payment.service.impl;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.dto.PaymentGatewayResponse;
import com.example.crud.data.payment.service.PaymentGatewayClient;
import org.springframework.stereotype.Service;

@Service
public class DummyPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public PaymentGatewayResponse processPayment(PaymentDto paymentDto) {
        // 실제 연동 시 PG사의 API를 호출하는 로직을 구현합니다.
        // 여기서는 Dummy로 항상 성공 처리
        PaymentGatewayResponse response = new PaymentGatewayResponse();
        response.setSuccess(true);
        response.setTransactionId("DUMMY_TXN_" + System.currentTimeMillis());
        response.setErrorMessage(null);
        return response;
    }
}
