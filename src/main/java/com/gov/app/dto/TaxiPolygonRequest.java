package com.gov.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaxiPolygonRequest {

    /** GeoJSON Polygon object. */
    @NotNull
    private JsonNode polygon;

    /** Optional target time (SGT, ISO-8601). When null, uses the latest snapshot. */
    private String datetime;
}
