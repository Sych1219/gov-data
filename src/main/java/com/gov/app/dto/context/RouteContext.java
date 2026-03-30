package com.gov.app.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class RouteContext implements QueryContext {
    JsonNode route;
    @JsonProperty("buffer_m")
    int bufferM;
}
