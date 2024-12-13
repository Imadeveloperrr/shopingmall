package com.example.crud.data.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // Authentication & Authorization
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "auth.invalid.refresh.token"),
    MISMATCH_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "auth.mismatch.refresh.token"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "auth.invalid.credentials"),

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

    public String getMessage() {
        return this.messageKey;
    }
}
