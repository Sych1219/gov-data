package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraAnalysisDetail {
    String congestion;
    String vehicleDensity;
    String incidents;
    String weather;
    String roadSurface;
    String summary;
    String analyzedAt;
}
