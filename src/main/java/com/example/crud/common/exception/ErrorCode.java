package com.example.crud.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Member Related Errors
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "member.invalid.password"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "member.password.mismatch"),

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
    PRODUCT_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "product.option.not.found"),

    // Order Related Errors,
    ORDER_STOCK_RESTORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "order.stock.restore.failed"),
    ORDER_ITEMS_EMPTY(HttpStatus.BAD_REQUEST, "order.items.empty"),
    ORDER_CANNOT_CANCEL(HttpStatus.BAD_REQUEST, "order.cannot.cancel"),
    ORDER_STATUS_CHANGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "order.status.change.not.allowed"),
    INVALID_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "order.invalid.amount"),

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "order.not.found"),
    ORDER_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "order.insufficient.stock"),
    ORDER_STATUS_UPDATE_FAILED(HttpStatus.BAD_REQUEST, "order.status.update.failed"),
    ORDER_CANCEL_FAILED(HttpStatus.BAD_REQUEST, "order.cancel.failed"),

    // Cart Related Errors
    CART_ACCESS_DENIED(HttpStatus.FORBIDDEN, "cart.access.denied"),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "cart.invalid.quantity"),

    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.not.found"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.item.not.found"),
    CART_INSUFFICIENT_QUANTITY(HttpStatus.BAD_REQUEST, "cart.insufficient.quantity"),
    CART_OPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "cart.option.not.found"),
    CART_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "cart.insufficient.stock"),

    // Payment Related Errors
    INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "payment.invalid.amount"),
    INVALID_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "payment.invalid.method"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "payment.amount.mismatch"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "payment.already.processed"),

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
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "server.error.internal"),

    // AI & Recommendation Errors
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "conversation.not.found"),
    CONVERSATION_UNAUTHORIZED(HttpStatus.FORBIDDEN, "conversation.unauthorized"),
    CONVERSATION_INACTIVE(HttpStatus.BAD_REQUEST, "conversation.inactive"),
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "ai.service.unavailable"),
    EMBEDDING_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "ai.embedding.failed"),
    INVALID_MESSAGE_INPUT(HttpStatus.BAD_REQUEST, "ai.invalid.message");


    private final HttpStatus status;
    private final String messageKey;

    ErrorCode(HttpStatus status, String messageKey) {
        this.status = status;
        this.messageKey = messageKey;
    }

}
