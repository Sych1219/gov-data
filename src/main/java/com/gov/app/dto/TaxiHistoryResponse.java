package com.gov.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder
public class TaxiHistoryResponse {

    List<SnapshotEntry> snapshots;

    @Value
    @Builder
    public static class SnapshotEntry {

        @JsonProperty("api_timestamp")
        OffsetDateTime apiTimestamp;

        @JsonProperty("taxi_count")
        int taxiCount;
    }
}
