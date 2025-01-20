package com.example.crud.data.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Authentication & Authorization
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "auth.invalid.refresh.token"),
    MISMATCH_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "auth.mismatch.refresh.token"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "auth.invalid.credentials"),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "auth.login.failed"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "auth.token.expired"),

    // Product Related Errors
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "product.not.found"),
    PRODUCT_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "product.upload.failed"),
    PRODUCT_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "product.update.failed"),
    PRODUCT_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "product.delete.failed"),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "product.image.upload.failed"),
    UNAUTHORIZED_PRODUCT_ACCESS(HttpStatus.FORBIDDEN, "product.access.denied"),

    // Order Related Errors
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "order.not.found"),
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "product.option.not.found"),
    ORDER_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "order.insufficient.stock"),
    ORDER_STATUS_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "order.status.update.failed"),
    ORDER_CANCEL_FAILED(HttpStatus.BAD_REQUEST, "order.cancel.failed"),

    // Cart Related Errors
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.not.found"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.item.not.found"),
    CART_INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "cart.insufficient.quantity"),
    CART_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.option.not.found"),
    CART_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "cart.insufficient.stock"),

    // Business Validation
    INVALID_MEMBER_UPDATE(HttpStatus.BAD_REQUEST, "member.invalid.update"),
    MEMBER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "member.access.denied"),

    // Resource Conflicts
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "member.duplicate.email"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "member.duplicate.nickname"),

    // Resource Not Found
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "member.not.found"),

    // Validation
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "validation.invalid.input"),

    // Server Errors
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "server.error.internal");

    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

}
