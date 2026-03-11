package com.gov.app.dto.context;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class ZoneContext implements QueryContext {
    @JsonProperty("zone_name")
    String zoneName;
    String category;
}
