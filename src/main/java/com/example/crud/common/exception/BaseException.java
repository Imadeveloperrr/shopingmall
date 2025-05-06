package com.example.crud.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String message;
    private final Object[] args;

    public BaseException(ErrorCode errorCode, Object... args) {
        this.errorCode = errorCode;
        this.message = errorCode.getMessageKey();
        this.args = args;
    }

    public BaseException(ErrorCode errorCode, String message, Object... args) {
        this.errorCode = errorCode;
        this.message = message;
        this.args = args;
    }
}
