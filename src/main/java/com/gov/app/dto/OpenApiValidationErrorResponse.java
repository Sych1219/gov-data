package com.gov.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenApiValidationErrorResponse {

    private String error;
    private String message;
    private List<String> validationErrors;
    private Instant timestamp;
    private String requestId;
}
