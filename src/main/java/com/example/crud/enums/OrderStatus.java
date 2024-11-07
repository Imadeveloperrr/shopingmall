package com.example.crud.enums;

public enum OrderStatus {
    ORDER_PLACED("주문접수"),
    PAYMENT_PENDING("결제대기"),
    PAYMENT_COMPLETED("결제완료"),
    PREPARING("상품준비중"),
    SHIPPING("배송중"),
    DELIVERED("배송완료"),
    CANCELLED("주문취소");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}