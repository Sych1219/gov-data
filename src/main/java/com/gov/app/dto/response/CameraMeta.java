package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CameraMeta {
    String expressway;
    String query;
    Double lat;
    Double lng;
    Integer radius;
    int count;
}
