package com.gov.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaxiPolygonRequest {

    /** GeoJSON Polygon object. */
    @Schema(
        description = "GeoJSON Polygon geometry object",
        example = "{\"type\":\"Polygon\",\"coordinates\":[[[103.8,1.28],[103.85,1.28],[103.85,1.32],[103.8,1.32],[103.8,1.28]]]}"
    )
    @NotNull
    private JsonNode polygon;

    /** Optional target time (SGT, ISO-8601). When null, uses the latest snapshot. */
    @Schema(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
    private String datetime;
}
