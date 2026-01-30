package com.gov.app.service;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedOpenApiSpec {

    private String title;
    private String version;
    private String description;
    private String baseUrl;
    private String openapiVersion;
    
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    
    @Builder.Default
    private List<ParsedEndpoint> endpoints = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedEndpoint {
        private String path;
        private String method;
        private String operationId;
        private String summary;
        private String description;
        private String parametersJson;
        private String requestBodyJson;
        private String responsesJson;
        private String securityJson;
        private List<String> tags;
    }
}
