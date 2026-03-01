package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class TaxiNearbyCountResponse {

    @JsonProperty("taxi_count")
    long taxiCount;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    QueryParams query;

    @Value
    @Builder
    public static class QueryParams {
        double lat;
        double lon;

        @JsonProperty("radius_m")
        int radiusM;
    }
}
