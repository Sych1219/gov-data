package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ZoneResolveResponse {

    String name;

    String category;

    @JsonProperty("suggested_endpoint")
    String suggestedEndpoint;
}
