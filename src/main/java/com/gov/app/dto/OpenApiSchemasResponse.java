package com.gov.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response wrapper for LLM-friendly endpoint schemas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiSchemasResponse {
    
    /**
     * List of all endpoint schemas
     */
    private List<OpenApiEndpointSchema> schemas;
}
