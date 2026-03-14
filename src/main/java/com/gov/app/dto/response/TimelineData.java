package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gov.app.dto.GeoJsonFeatureCollection;
import com.gov.app.dto.context.QueryContext;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public final class TimelineData implements TaxiResponseData {

    @JsonProperty("from_time")
    OffsetDateTime fromTime;

    @JsonProperty("to_time")
    OffsetDateTime toTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("window_minutes")
    Integer windowMinutes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    QueryContext context;

    List<SnapshotEntry> snapshots;

    @JsonProperty("type")
    public String getType() { return "timeline"; }

    @Value
    @Builder
    public static class SnapshotEntry {
        OffsetDateTime timestamp;

        @JsonProperty("taxi_count")
        int taxiCount;

        GeoJsonFeatureCollection locations;
    }
}
