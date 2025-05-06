package com.example.crud.data.payment.service.impl;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.dto.PaymentGatewayResponse;
import com.example.crud.data.payment.service.PaymentGatewayClient;
import com.example.crud.data.payment.service.PaymentService;
import com.example.crud.entity.Orders;
import com.example.crud.entity.PaymentHistory;
import com.example.crud.repository.OrderRepository;
import com.example.crud.repository.PaymentHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentGatewayClient paymentGatewayClient;

    @Override
    public boolean processPayment(PaymentDto paymentDto) {
        Orders order = orderRepository.findById(paymentDto.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // 결제 게이트웨이와 연동하여 결제 처리 (여기서는 Dummy 구현)
        PaymentGatewayResponse gatewayResponse = paymentGatewayClient.processPayment(paymentDto);

        // 결제 내역 기록을 위한 PaymentHistory 객체 생성
        PaymentHistory history = PaymentHistory.builder()
                .orderId(paymentDto.getOrderId())
                .amount(paymentDto.getAmount())
                .paymentMethod(paymentDto.getPaymentMethod())
                .createdAt(LocalDateTime.now())
                .build();

        if (gatewayResponse.isSuccess()) {
            order.setPaymentStatus("PAID");
            history.setStatus("SUCCESS");
            history.setTransactionId(gatewayResponse.getTransactionId());
            paymentDto.setTransactionId(gatewayResponse.getTransactionId());
        } else {
            // 결제 실패 시 주문 상태 업데이트 및 에러 처리
            order.setPaymentStatus("FAILED");
            history.setStatus("FAILED");
            history.setTransactionId(null);
        }

        orderRepository.save(order);
        paymentHistoryRepository.save(history);

        return gatewayResponse.isSuccess();
    }

    @Override
    public Orders getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }
}
