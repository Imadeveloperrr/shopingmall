package com.example.crud.data.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final MessageSource messageSource;

    private void logException(String type, Exception e) {
        log.error("{}: {}", type, e.getMessage(), e);
    }

    @ExceptionHandler(BaseException.class)
    protected ResponseEntity<ErrorResponse> handleBaseException(
            BaseException e, HttpServletRequest request, Locale locale) {
        logException("BaseException", e);

        String message = messageSource.getMessage(
                e.getErrorCode().getMessageKey(),
                e.getArgs(),
                e.getMessage(),
                locale
        );

        ErrorResponse response = ErrorResponse.of(
                e.getErrorCode(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(e.getErrorCode().getStatus()).body(response);
    }

    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException e, HttpServletRequest request) {
        logException("ValidationException", e);

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ErrorCode.INVALID_INPUT.getStatus().value())
                .error(ErrorCode.INVALID_INPUT.getStatus().getReasonPhrase())
                .message("Validation failed")
                .path(request.getRequestURI())
                .validationErrors(e.getValidationErrors())
                .build();

        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error("Validation error: {}", e.getMessage());

        List<ErrorResponse.ValidationError> validationErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> ErrorResponse.ValidationError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .path(request.getRequestURI())
                .validationErrors(validationErrors)
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException e, HttpServletRequest request) {
        log.error("Authentication error: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INVALID_CREDENTIALS,
                request.getRequestURI()
        );

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException e, HttpServletRequest request) {
        log.error("Access denied: {}", e.getMessage());

        ErrorResponse response = ErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "접근이 거부되었습니다.",
                request.getRequestURI()
        );

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(
            Exception e, HttpServletRequest request) {
        log.error("Unhandled exception: ", e);

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                request.getRequestURI()
        );

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
