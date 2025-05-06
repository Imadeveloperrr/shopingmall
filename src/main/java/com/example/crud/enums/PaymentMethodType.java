package com.example.crud.enums;

public enum PaymentMethodType {
    CREDIT_CARD("신용카드"),
    BANK_TRANSFER("무통장입금"),
    VIRTUAL_ACCOUNT("가상계좌"),
    MOBILE_PAYMENT("휴대폰결제"),
    KAKAO_PAY("카카오페이"),
    PAYCO("페이코"),
    SAMSUNG_PAY("삼성페이");

    private final String description;

    PaymentMethodType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
