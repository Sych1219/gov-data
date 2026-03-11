package com.gov.app.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class RadiusContext implements QueryContext {
    double lat;
    double lon;
    @JsonProperty("radius_m")
    int radiusM;
}
