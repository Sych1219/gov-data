package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraDetail {
    Long cameraId;
    String locationName;
    double latitude;
    double longitude;
    String latestImage;
    String timestamp;
    String resolution;
}
