package com.gov.app.service;

public interface OpenApiValidationService {

    /**
     * Validates OpenAPI specification
     * @param openApiJson Raw OpenAPI JSON
     * @return Validation result with errors/warnings
     */
    OpenApiValidationResult validate(String openApiJson);

    /**
     * Parses and extracts metadata from OpenAPI spec
     * @param openApiJson Valid OpenAPI JSON
     * @return Parsed specification with metadata
     */
    ParsedOpenApiSpec parse(String openApiJson);
}
