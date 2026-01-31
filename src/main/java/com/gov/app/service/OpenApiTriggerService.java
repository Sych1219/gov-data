package com.gov.app.service;

import com.gov.app.dto.GovApiTriggerRequestV2;
import com.gov.app.dto.GovApiTriggerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for triggering OpenAPI-based government APIs (V2).
 * Uses stored OpenAPI specification metadata to construct and execute HTTP requests.
 */
public interface OpenApiTriggerService {

    /**
     * Triggers an OpenAPI endpoint with provided parameters.
     * 
     * @param endpointId UUID of the endpoint to trigger from gov_openapi_endpoint table
     * @param request Trigger request with queryParams and bodyParams
     * @param requestId Correlation ID for tracing
     * @return Response with upstream API results
     */
    Mono<GovApiTriggerResponse> trigger(UUID endpointId, GovApiTriggerRequestV2 request, String requestId);
}
