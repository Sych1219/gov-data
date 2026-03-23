package com.gov.app.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class SearchResponse {
    String query;
    List<CameraDetail> cameras;
}
