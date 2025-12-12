package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
public class ErrorResponse {
    private final String error;
    private final String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Integer externalStatus;

    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }

    public ErrorResponse(String error, String message, Integer externalStatus) {
        this.error = error;
        this.message = message;
        this.externalStatus = externalStatus;
    }
}
