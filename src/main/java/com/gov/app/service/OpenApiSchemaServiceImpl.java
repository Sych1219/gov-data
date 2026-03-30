package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.GovOpenApiEndpoint;
import com.gov.app.dto.OpenApiEndpointSchema;
import com.gov.app.dto.OpenApiSchemasResponse;
import com.gov.app.repository.GovOpenApiEndpointRepository;
import com.gov.app.repository.GovOpenApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of OpenAPI schema service for LLM consumption
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OpenApiSchemaServiceImpl implements OpenApiSchemaService {

    private final GovOpenApiEndpointRepository endpointRepository;
    private final GovOpenApiRegistrationRepository registrationRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<OpenApiSchemasResponse> getAllEndpointSchemas() {
        log.info("Retrieving all endpoint schemas for LLM");
        
        return endpointRepository.findAll()
                .flatMap(this::buildEndpointSchema)
                .collectList()
                .map(schemas -> {
                    log.info("All schemas retrieved successfully. TotalEndpoints: {}", schemas.size());
                    return OpenApiSchemasResponse.builder()
                            .schemas(schemas)
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("Failed to retrieve endpoint schemas", e);
                    return Mono.error(e);
                });
    }

    /**
     * Build a complete endpoint schema with description and parameters
     */
    private Mono<OpenApiEndpointSchema> buildEndpointSchema(GovOpenApiEndpoint endpoint) {
        return registrationRepository.findById(endpoint.getOpenapiId())
                .map(registration -> {
                    String description = buildLlmFriendlyDescription(
                            registration.getTitle(),
                            endpoint.getSummary(),
                            endpoint.getDescription()
                    );
                    
                    List<OpenApiEndpointSchema.Parameter> parameters = extractAllParameters(endpoint);
                    JsonNode responseFormat = extractResponseFormatFromSpec(
                            registration.getOpenapiSpecJson(), endpoint.getPath(), endpoint.getHttpMethod());
                    
                    return OpenApiEndpointSchema.builder()
                            .id(endpoint.getId())
                            .description(description)
                            .parameters(parameters)
                            .responseFormat(responseFormat)
                            .build();
                })
                .defaultIfEmpty(OpenApiEndpointSchema.builder()
                        .id(endpoint.getId())
                        .description(buildLlmFriendlyDescription(null, endpoint.getSummary(), endpoint.getDescription()))
                        .parameters(extractAllParameters(endpoint))
                        .build());
    }

    /**
     * Build LLM-friendly description with usage hints
     */
    private String buildLlmFriendlyDescription(String title, String summary, String description) {
        StringBuilder sb = new StringBuilder();
        
        // Primary content - prefer description, then summary, then title
        if (description != null && !description.trim().isEmpty()) {
            sb.append(description);
        } else if (summary != null && !summary.trim().isEmpty()) {
            sb.append(summary);
        } else if (title != null && !title.trim().isEmpty()) {
            sb.append(title);
        } else {
            sb.append("API endpoint");
        }
        
        // Add usage hints based on keywords
        String combined = ((title != null ? title : "") + " " + 
                          (summary != null ? summary : "") + " " + 
                          (description != null ? description : "")).toLowerCase();
        
        if (combined.contains("weather") || combined.contains("temperature") || combined.contains("climate")) {
            sb.append(" Use this API when users ask for temperature, weather conditions, or climate data.");
        } else if (combined.contains("create") || combined.contains("add") || combined.contains("register")) {
            sb.append(" Use this when user wants to add, create, or register new data.");
        } else if (combined.contains("search") || combined.contains("query") || combined.contains("find") || combined.contains("filter")) {
            sb.append(" Use this when user wants to find, search, or filter data.");
        } else if (combined.contains("update") || combined.contains("modify") || combined.contains("edit")) {
            sb.append(" Use this when user wants to update, modify, or change existing data.");
        } else if (combined.contains("delete") || combined.contains("remove")) {
            sb.append(" Use this when user wants to delete or remove data.");
        } else if (combined.contains("list") || combined.contains("get all") || combined.contains("browse")) {
            sb.append(" Use this when user wants to see all records or browse data.");
        }
        
        return sb.toString();
    }

    /**
     * Extract all parameters from both query parameters and request body
     */
    private List<OpenApiEndpointSchema.Parameter> extractAllParameters(GovOpenApiEndpoint endpoint) {
        List<OpenApiEndpointSchema.Parameter> allParameters = new ArrayList<>();
        
        // Extract query parameters
        if (endpoint.getParametersJson() != null && !endpoint.getParametersJson().trim().isEmpty()) {
            allParameters.addAll(extractQueryParameters(endpoint.getParametersJson()));
        }
        
        // Extract body parameters
        if (endpoint.getRequestBodyJson() != null && !endpoint.getRequestBodyJson().trim().isEmpty()) {
            allParameters.addAll(extractBodyParameters(endpoint.getRequestBodyJson()));
        }
        
        return allParameters;
    }

    /**
     * Extract query parameters from parameters_json
     * Only includes parameters where "in" = "query"
     * Excludes header, path, and cookie parameters (handled by backend)
     */
    private List<OpenApiEndpointSchema.Parameter> extractQueryParameters(String parametersJson) {
        List<OpenApiEndpointSchema.Parameter> parameters = new ArrayList<>();
        
        try {
            JsonNode paramArray = objectMapper.readTree(parametersJson);
            
            if (paramArray.isArray()) {
                for (JsonNode param : paramArray) {
                    String in = param.path("in").asText();
                    
                    // Only include query parameters
                    // Exclude: header (auth/security), path (routing), cookie (session)
                    if (!"query".equals(in)) {
                        log.debug("Excluding non-query parameter from LLM schema: {} (in: {})", 
                                param.path("name").asText(), in);
                        continue;
                    }
                    
                    String name = param.path("name").asText();
                    boolean required = param.path("required").asBoolean(false);
                    String type = extractType(param.path("schema"));
                    String description = param.path("description").asText(name + " (" + type + ")");
                    
                    parameters.add(OpenApiEndpointSchema.Parameter.builder()
                            .name(name)
                            .location("query")
                            .required(required)
                            .type(type)
                            .description(description)
                            .build());
                }
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse parameters JSON: {}", e.getMessage());
        }
        
        return parameters;
    }

    /**
     * Extract body parameters from request_body_json
     */
    private List<OpenApiEndpointSchema.Parameter> extractBodyParameters(String requestBodyJson) {
        List<OpenApiEndpointSchema.Parameter> parameters = new ArrayList<>();
        
        try {
            JsonNode requestBody = objectMapper.readTree(requestBodyJson);
            
            // Navigate to schema properties
            JsonNode content = requestBody.path("content");
            JsonNode jsonContent = content.path("application/json");
            if (jsonContent.isMissingNode()) {
                // Try first available content type
                if (content.isObject() && content.fields().hasNext()) {
                    jsonContent = content.fields().next().getValue();
                }
            }
            
            JsonNode schema = jsonContent.path("schema");
            JsonNode properties = schema.path("properties");
            JsonNode requiredArray = schema.path("required");
            
            // Build set of required fields
            List<String> requiredFields = new ArrayList<>();
            if (requiredArray.isArray()) {
                requiredArray.forEach(node -> requiredFields.add(node.asText()));
            }
            
            // Process each property
            if (properties.isObject()) {
                properties.fields().forEachRemaining(entry -> {
                    String propName = entry.getKey();
                    JsonNode propSchema = entry.getValue();
                    
                    boolean isRequired = requiredFields.contains(propName);
                    String type = extractType(propSchema);
                    String description = propSchema.path("description").asText(propName + " (" + type + ")");
                    
                    parameters.add(OpenApiEndpointSchema.Parameter.builder()
                            .name(propName)
                            .location("body")
                            .required(isRequired)
                            .type(type)
                            .description(description)
                            .build());
                });
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse request body JSON: {}", e.getMessage());
        }
        
        return parameters;
    }

    /**
     * Extract the responses object from the full OpenAPI spec JSON for a specific path and method.
     * Navigates: paths -> {path} -> {method} -> responses
     */
    private JsonNode extractResponseFormatFromSpec(String openapiSpecJson, String path, String httpMethod) {
        if (openapiSpecJson == null || openapiSpecJson.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode spec = objectMapper.readTree(openapiSpecJson);
            JsonNode responses = spec.path("paths")
                    .path(path)
                    .path(httpMethod.toLowerCase())
                    .path("responses");
            if (responses.isMissingNode()) {
                return null;
            }
            return responses;
        } catch (JsonProcessingException e) {
            log.warn("Failed to extract response format from OpenAPI spec: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract type from schema node
     */
    private String extractType(JsonNode schema) {
        if (schema.isMissingNode()) {
            return "string";
        }
        
        String type = schema.path("type").asText("string");
        
        // Handle integer format
        if ("integer".equals(type)) {
            return "integer";
        }
        
        // Handle number types
        if ("number".equals(type)) {
            return "number";
        }
        
        // Map common types
        switch (type) {
            case "boolean":
                return "boolean";
            case "array":
                return "array";
            case "object":
                return "object";
            default:
                return "string";
        }
    }
}
