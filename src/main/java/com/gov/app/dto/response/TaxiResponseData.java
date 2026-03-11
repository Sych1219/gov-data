package com.gov.app.dto.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SpatialQueryData.class, name = "spatial_query"),
    @JsonSubTypes.Type(value = TimelineData.class,     name = "timeline"),
    @JsonSubTypes.Type(value = ZoneGeometryData.class, name = "zone_geometry"),
})
public sealed interface TaxiResponseData permits SpatialQueryData, TimelineData, ZoneGeometryData {}
