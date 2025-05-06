package com.example.crud.data.payment.service;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.entity.Orders;

public interface PaymentService {
    /**
     * 결제 처리를 진행하고 결과에 따라 주문 상태를 업데이트합니다.
     *
     * @param paymentDto 결제 요청 데이터
     * @return 결제 성공 여부
     */
    boolean processPayment(PaymentDto paymentDto);

    /**
     * 주문 번호로 주문 정보를 조회합니다.
     */
    Orders getOrderById(Long orderId);
}
