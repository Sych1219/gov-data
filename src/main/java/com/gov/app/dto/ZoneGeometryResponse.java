package com.gov.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * GeoJSON Feature representing the geometry of a zone (district, road, or highway).
 * The {@code geometry} field is a raw GeoJSON geometry object (Polygon or LineString)
 * returned directly from PostGIS via ST_AsGeoJSON.
 */
@Value
@Builder
public class ZoneGeometryResponse {

    String type = "Feature";

    Properties properties;

    JsonNode geometry;

    @Value
    @Builder
    public static class Properties {
        String name;
        String category;
    }
}
