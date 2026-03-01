package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TaxiNearestResponse {

    List<TaxiPoint> taxis;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    @Value
    @Builder
    public static class TaxiPoint {
        double longitude;
        double latitude;

        @JsonProperty("distance_m")
        double distanceM;
    }
}
