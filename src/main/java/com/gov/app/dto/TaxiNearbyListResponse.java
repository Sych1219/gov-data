package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TaxiNearbyListResponse {

    /** GeoJSON type — always "FeatureCollection". */
    String type = "FeatureCollection";

    List<Feature> features;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    @Value
    @Builder
    public static class Feature {
        String type = "Feature";
        Geometry geometry;
    }

    @Value
    @Builder
    public static class Geometry {
        String type = "Point";
        double[] coordinates; // [longitude, latitude]
    }
}
