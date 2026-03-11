package com.gov.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TaxiRouteRequest {

    @NotNull
    private JsonNode route;

    @Positive
    @JsonProperty("buffer_m")
    private int bufferM = 200;

    private String datetime;
}
