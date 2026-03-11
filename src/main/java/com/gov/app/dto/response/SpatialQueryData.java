package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gov.app.dto.GeoJsonFeatureCollection;
import com.gov.app.dto.context.QueryContext;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public final class SpatialQueryData implements TaxiResponseData {

    @JsonProperty("taxi_count")
    int taxiCount;

    @JsonProperty("snapshot_time")
    OffsetDateTime snapshotTime;

    QueryContext context;

    GeoJsonFeatureCollection locations;

    @JsonProperty("type")
    public String getType() { return "spatial_query"; }
}
