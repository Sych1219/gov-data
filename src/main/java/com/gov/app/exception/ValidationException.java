package com.gov.app.exception;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ValidationException extends RuntimeException {

    private List<String> validationErrors;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }
}
