package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public final class ZoneGeometryData implements TaxiResponseData {
    String name;
    String category;
    JsonNode geometry;

    @JsonProperty("type")
    public String getType() { return "zone_geometry"; }
}
