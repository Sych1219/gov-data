package com.gov.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SaveTrajectoryRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String agent;

    @NotBlank
    private String question;

    @NotBlank
    private String eventsJson;

    @Positive
    private int iterations;
}
