package com.example.crud.data.exception;

import lombok.Getter;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ValidationException extends BaseException {
    private final List<ErrorResponse.ValidationError> validationErrors;

    public ValidationException(List<ObjectError> errors) {
        super(ErrorCode.INVALID_INPUT);
        this.validationErrors = errors.stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> {
                    FieldError fieldError = (FieldError) error;
                    return ErrorResponse.ValidationError.builder()
                            .field(fieldError.getField())
                            .message(fieldError.getDefaultMessage())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
