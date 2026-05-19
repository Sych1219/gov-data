package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraQueryResponse {
    List<CameraDetail> cameras;
    CameraMeta meta;
}
