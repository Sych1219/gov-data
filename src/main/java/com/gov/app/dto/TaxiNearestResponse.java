package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TaxiNearestResponse {

    @JsonProperty("taxi_count")
    int taxiCount;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    QueryParams query;

    Locations locations;

    @Value
    @Builder
    public static class QueryParams {
        double lat;
        double lon;
        int limit;
    }

    @Value
    @Builder
    public static class Locations {
        String type = "FeatureCollection";
        List<Feature> features;
    }

    @Value
    @Builder
    public static class Feature {
        String type = "Feature";
        Geometry geometry;
        Properties properties;
    }

    @Value
    @Builder
    public static class Geometry {
        String type = "Point";
        double[] coordinates; // [longitude, latitude]
    }

    @Value
    @Builder
    public static class Properties {
        @JsonProperty("distance_m")
        double distanceM;
    }
}
