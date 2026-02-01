package com.gov.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * LLM-friendly endpoint schema with simplified parameter structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiEndpointSchema {
    
    /**
     * Endpoint UUID for triggering via /trigger API
     */
    private UUID id;
    
    /**
     * High-level description of what this endpoint does and when to use it
     * Written in LLM-friendly language with usage hints
     */
    private String description;
    
    /**
     * List of all parameters (query + body) needed for this endpoint
     * Flat array with location field differentiating query vs body
     */
    private List<Parameter> parameters;
    
    /**
     * Individual parameter definition for LLM consumption
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        
        /**
         * Parameter name (used as key in queryParams or bodyParams)
         */
        private String name;
        
        /**
         * Where to place this parameter: "query" or "body"
         */
        private String location;
        
        /**
         * Whether this parameter must be provided
         */
        private Boolean required;
        
        /**
         * Data type: "string", "integer", "boolean", "number", "array", "object"
         */
        private String type;
        
        /**
         * LLM-friendly description including:
         * - What this parameter represents
         * - Format requirements (with examples)
         * - How to extract from user input
         * - Default behavior if not provided
         */
        private String description;
    }
}
