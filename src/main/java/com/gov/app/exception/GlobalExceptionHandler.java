package com.gov.app.exception;

import com.gov.app.dto.response.ApiError;
import com.gov.app.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException ex) {
        Object details = (ex.getValidationErrors() != null && !ex.getValidationErrors().isEmpty())
                ? ex.getValidationErrors() : null;
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            errors.put(field, v.getMessage());
        });
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", errors);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(UpstreamException ex) {
        log.error("Upstream error ({}): {}", ex.getStatusCode(), ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", ex.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), null);
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ApiResponse<Void>> handleNumberFormat(NumberFormatException ex) {
        log.warn("Number format error: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid number format: " + ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled {} — {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message, Object details) {
        return ResponseEntity.status(status).body(
                ApiResponse.fail(ApiError.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build()));
    }
}
