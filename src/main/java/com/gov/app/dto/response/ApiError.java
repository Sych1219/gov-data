package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiError {
    String code;
    String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Object details;
}
