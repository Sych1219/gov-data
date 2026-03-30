package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class CameraListAllResponse {
    List<CameraDetail> cameras;
}
