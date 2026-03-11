package com.gov.app.dto.request;

import com.gov.app.dto.GeoJsonPolygon;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaxiPolygonRequest {

    @Schema(
        description = "GeoJSON Polygon geometry object",
        example = "{\"type\":\"Polygon\",\"coordinates\":[[[103.8,1.28],[103.85,1.28],[103.85,1.32],[103.8,1.32],[103.8,1.28]]]}"
    )
    @NotNull
    @Valid
    private GeoJsonPolygon polygon;

    @Schema(description = "Target time ISO-8601 SGT. Defaults to latest snapshot.", example = "2025-01-15T08:30:00+08:00")
    private String datetime;
}
