package com.gov.app.exception;

import com.gov.app.dto.ErrorResponse;
import com.gov.app.dto.OpenApiValidationErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    @ExceptionHandler({WebExchangeBindException.class, MethodArgumentNotValidException.class, ServerWebInputException.class})
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(Exception ex) {
        String message;
        if (ex instanceof WebExchangeBindException bindException) {
            message = bindException.getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .orElse("Request validation failed");
        } else if (ex instanceof MethodArgumentNotValidException methodArgumentEx) {
            message = methodArgumentEx.getAllErrors().stream()
                    .map(MessageSourceResolvable::getDefaultMessage)
                    .findFirst()
                    .orElse("Request validation failed");
        } else {
            message = ex.getMessage();
        }
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Request validation failed");
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTriggerValidation(ValidationException ex) {
        // Check if this is an OpenAPI validation with multiple errors
        if (ex.getValidationErrors() != null && !ex.getValidationErrors().isEmpty()) {
            OpenApiValidationErrorResponse response = new OpenApiValidationErrorResponse(
                    "INVALID_OPENAPI_SPEC",
                    ex.getMessage(),
                    ex.getValidationErrors(),
                    Instant.now(),
                    null // RequestId would come from context in a real implementation
            );
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response))
                    .map(r -> (ResponseEntity<ErrorResponse>) (ResponseEntity<?>) r);
        }
        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleFileSizeException(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "OpenAPI specification exceeds maximum size of 5MB");
    }

    @ExceptionHandler(ConflictException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(ApiRegistrationNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleApiRegistrationNotFound(ApiRegistrationNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "API_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusiness(BusinessException ex) {
        return build(HttpStatus.BAD_REQUEST, "BUSINESS_ERROR", ex.getMessage());
    }

    @ExceptionHandler(UpstreamException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUpstream(UpstreamException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("UPSTREAM_ERROR", ex.getMessage(), ex.getStatusCode())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "UNKNOWN_ERROR", "Internal error");
    }

    private Mono<ResponseEntity<ErrorResponse>> build(HttpStatus status, String errorCode, String message) {
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(errorCode, message)));
    }
}
