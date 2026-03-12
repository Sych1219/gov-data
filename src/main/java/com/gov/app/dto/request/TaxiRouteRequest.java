package com.gov.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.gov.app.validation.Iso8601Sgt;
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

    @Iso8601Sgt
    private String datetime;
}
