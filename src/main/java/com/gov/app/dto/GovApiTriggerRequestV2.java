package com.gov.app.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * V2 simplified trigger request for OpenAPI-based endpoints.
 * Removed complexity from V1: no useExampleDefaults, no headerOverrides, flat parameter maps.
 */
@Getter
@Setter
public class GovApiTriggerRequestV2 {

    /**
     * Flat map of query parameter key-value pairs.
     * Keys must match parameter names defined in the OpenAPI specification.
     * Only include for endpoints that accept query parameters.
     */
    private Map<String, Object> queryParams;

    /**
     * Flat map of request body field key-value pairs.
     * Keys must match schema properties in the OpenAPI requestBody definition.
     * Only include for POST/PUT/PATCH endpoints with request bodies.
     */
    private Map<String, Object> bodyParams;

    @AssertTrue(message = "queryParams must not be empty when provided")
    public boolean isQueryParamsValid() {
        return queryParams == null || !queryParams.isEmpty();
    }

    @AssertTrue(message = "bodyParams must not be empty when provided")
    public boolean isBodyParamsValid() {
        return bodyParams == null || !bodyParams.isEmpty();
    }
}
