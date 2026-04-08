package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StoreAnalysisResponse {
    Long cameraId;
    String analyzedAt;
}
