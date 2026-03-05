package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class TaxiRouteCountResponse {

    @JsonProperty("taxi_count")
    long taxiCount;

    @JsonProperty("buffer_m")
    int bufferM;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    GeoJsonFeatureCollection locations;
}
