package com.example.crud.data.exception;

import lombok.Getter;
import org.springframework.validation.ObjectError;

import java.util.List;

@Getter
public class ValidationException extends BaseException {
    private final List<ObjectError> errors;

    public ValidationException(List<ObjectError> errors) {
        super(ErrorCode.INVALID_INPUT);
        this.errors = errors;
    }
}
