package com.gov.app.dto.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CivicAppAnalysisResponse {

    private Analysis analysis;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Analysis {
        private String congestion;

        @JsonProperty("vehicle_density")
        private String vehicleDensity;

        private String incidents;

        private String weather;

        @JsonProperty("road_surface")
        private String roadSurface;

        private String summary;
    }
}
