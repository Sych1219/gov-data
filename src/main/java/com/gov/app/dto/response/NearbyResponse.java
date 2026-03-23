package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class NearbyResponse {
    double lat;
    double lng;
    int radius;
    List<CameraDetail> cameras;
}
