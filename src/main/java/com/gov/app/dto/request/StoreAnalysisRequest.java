package com.gov.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class StoreAnalysisRequest {

    @NotBlank(message = "congestion is required")
    String congestion;

    @NotBlank(message = "vehicleDensity is required")
    String vehicleDensity;

    @NotBlank(message = "incidents is required")
    String incidents;

    @NotBlank(message = "weather is required")
    String weather;

    @NotBlank(message = "roadSurface is required")
    String roadSurface;

    @NotBlank(message = "summary is required")
    String summary;
}
