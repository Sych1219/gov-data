package com.gov.app.service;

import com.gov.app.dto.OpenApiRegistrationResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface OpenApiRegistrationService {

    /**
     * Registers a new OpenAPI specification
     * @param parsedSpec Already validated and parsed OpenAPI specification
     * @param openApiJson Raw OpenAPI JSON string for storage
     * @return Registration response with ID and summary
     */
    Mono<OpenApiRegistrationResponse> register(ParsedOpenApiSpec parsedSpec, String openApiJson);

    /**
     * Checks if API is already registered
     * @param title API title (from info.title)
     * @param baseUrl Base URL (from servers[0].url)
     * @return true if already exists
     */
    Mono<Boolean> isAlreadyRegistered(String title, String baseUrl);

    /**
     * Retrieves OpenAPI spec by ID
     * @param id Registration ID
     * @return Complete OpenAPI specification
     */
    Mono<OpenApiRegistrationResponse> getById(UUID id);
}
