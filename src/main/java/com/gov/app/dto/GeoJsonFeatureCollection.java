package com.gov.app.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Minimal GeoJSON FeatureCollection used as the {@code locations} field in count responses.
 * Reuses the Feature/Geometry inner types from {@link TaxiNearbyListResponse}.
 */
@Value
@Builder
public class GeoJsonFeatureCollection {

    String type = "FeatureCollection";

    List<TaxiNearbyListResponse.Feature> features;
}
