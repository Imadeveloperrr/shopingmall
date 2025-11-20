package com.example.crud.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = ex.getMessage();
        ErrorResponse body;
        if (ex instanceof ValidationException validationException && validationException.getValidationErrors() != null) {
            body = ErrorResponse.builder()
                    .timestamp(java.time.LocalDateTime.now())
                    .status(errorCode.getStatus().value())
                    .error(errorCode.getStatus().getReasonPhrase())
                    .message(message)
                    .path(request.getRequestURI())
                    .validationErrors(validationException.getValidationErrors())
                    .build();
        } else {
            body = ErrorResponse.of(errorCode, message, request.getRequestURI());
        }
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidationException(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        List<ErrorResponse.ValidationError> validationErrors;

        if (ex instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            validationErrors = methodArgumentNotValidException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> ErrorResponse.ValidationError.builder()
                            .field(error.getField())
                            .message(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());
        } else if (ex instanceof BindException bindException) {
            validationErrors = bindException.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> ErrorResponse.ValidationError.builder()
                            .field(error.getField())
                            .message(error.getDefaultMessage())
                            .build())
                    .collect(Collectors.toList());
        } else {
            validationErrors = List.of();
        }

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(java.time.LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ErrorCode.INVALID_INPUT.getMessageKey())
                .path(request.getRequestURI())
                .validationErrors(validationErrors.isEmpty() ? null : validationErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletionException(CompletionException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof BaseException baseException) {
            return handleBaseException(baseException, request);
        }
        if (cause instanceof MethodArgumentNotValidException || cause instanceof BindException) {
            return handleValidationException((Exception) cause, request);
        }
        if (cause instanceof Exception exception) {
            return handleGenericException(exception, request);
        }
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
