package com.gov.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiRegistrationResponse {

    private UUID id;
    private String title;
    private String version;
    private String baseUrl;
    private Integer endpointCount;
    private List<EndpointSummary> endpoints;
    private String status;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointSummary {
        private String path;
        private String method;
        private String summary;
    }
}
