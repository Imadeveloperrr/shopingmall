package com.example.crud.data.payment.service;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.dto.PaymentGatewayResponse;

public interface PaymentGatewayClient {
    PaymentGatewayResponse processPayment(PaymentDto paymentDto);
}
