package com.gov.app.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class RoadContext implements QueryContext {
    @JsonProperty("road_name")
    String roadName;
    String category;
    @JsonProperty("buffer_m")
    int bufferM;
}
