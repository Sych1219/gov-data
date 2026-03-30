package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GeoJsonFeature {

    String type = "Feature";
    Geometry geometry;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    Object properties;

    @Value
    @Builder
    public static class Geometry {
        String type = "Point";
        double[] coordinates;
    }
}
