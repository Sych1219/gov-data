package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TaxiTimelineResponse {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("window_minutes")
    Integer windowMinutes;

    @JsonProperty("from_time")
    OffsetDateTime fromTime;

    @JsonProperty("to_time")
    OffsetDateTime toTime;

    List<SnapshotEntry> snapshots;

    @Value
    @Builder
    public static class SnapshotEntry {

        OffsetDateTime timestamp;

        @JsonProperty("taxi_count")
        int taxiCount;

        GeoJsonFeatureCollection locations;
    }
}
