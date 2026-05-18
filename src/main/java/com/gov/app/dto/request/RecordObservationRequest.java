package com.gov.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RecordObservationRequest {

    @NotBlank
    private String requestId;

    private boolean hintPresent;

    @Positive
    private int iterations;

    private boolean success;
}
