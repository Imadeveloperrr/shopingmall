package com.example.crud.data.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않은 Refresh Token입니다."),
    MISMATCH_REFRESH_TOKEN(HttpStatus.BAD_REQUEST, "토큰의 유저 정보가 일치하지 않습니다."),
    // 기타 에러 코드 추가...

    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
