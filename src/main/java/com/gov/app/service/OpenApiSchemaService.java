package com.gov.app.service;

import com.gov.app.dto.OpenApiSchemasResponse;
import reactor.core.publisher.Mono;

/**
 * Service for providing LLM-friendly endpoint schemas
 */
public interface OpenApiSchemaService {
    
    /**
     * Retrieves LLM-friendly schemas for all registered endpoints
     * 
     * @return List of simplified schemas with descriptions and parameters
     */
    Mono<OpenApiSchemasResponse> getAllEndpointSchemas();
}
