package com.gov.app.dto.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps the GeoJSON FeatureCollection returned by data.gov.sg taxi-availability API.
 *
 * Response shape:
 * {
 *   "type": "FeatureCollection",
 *   "features": [{
 *     "geometry": { "type": "MultiPoint", "coordinates": [[103.123, 1.456], ...] },
 *     "properties": { "timestamp": "2026-02-28T08:00:00+08:00", "taxi_count": 7843 }
 *   }]
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GovTaxiResponse {

    private List<Feature> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Feature {
        private Geometry geometry;
        private Properties properties;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Geometry {
        /** Each element is [longitude, latitude]. */
        private List<List<Double>> coordinates;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        private String timestamp;

        @JsonProperty("taxi_count")
        private int taxiCount;
    }
}
