package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TaxiRouteRequest {

    /** GeoJSON LineString object. */
    @NotNull
    private JsonNode route;

    /** Buffer distance in metres around the route. Default 200. */
    @Positive
    @JsonProperty("buffer_m")
    private int bufferM = 200;

    /** Optional target time (SGT, ISO-8601). When null, uses the latest snapshot. */
    private String datetime;
}
