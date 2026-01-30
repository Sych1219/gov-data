package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.exception.ValidationException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenApiValidationServiceImpl implements OpenApiValidationService {

    private final ObjectMapper objectMapper;

    @Value("${gov-api.openapi.max-endpoints:50}")
    private int maxEndpoints;

    @Override
    public OpenApiValidationResult validate(String openApiJson) {
        OpenApiValidationResult result = OpenApiValidationResult.builder()
                .valid(true)
                .build();

        try {
            // 1. Validate JSON format
            JsonNode jsonNode = objectMapper.readTree(openApiJson);

            // 2. Validate OpenAPI structure
            validateOpenApiStructure(jsonNode, result);

            if (!result.isValid()) {
                return result;
            }

            // 3. Parse with Swagger Parser
            ParseOptions parseOptions = new ParseOptions();
            parseOptions.setResolve(true);
            parseOptions.setResolveFully(true);

            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            SwaggerParseResult parseResult = parser.readContents(openApiJson, null, parseOptions);

            if (parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()) {
                parseResult.getMessages().forEach(result::addError);
                result.setValid(false);
                return result;
            }

            OpenAPI openAPI = parseResult.getOpenAPI();
            if (openAPI == null) {
                result.addError("Failed to parse OpenAPI specification");
                return result;
            }

            // 4. Business validation
            validateBusinessRules(openAPI, result);

            // 5. If valid, parse and extract metadata
            if (result.isValid()) {
                ParsedOpenApiSpec parsedSpec = extractMetadata(openAPI, openApiJson);
                result.setParsedSpec(parsedSpec);
            }

        } catch (JsonProcessingException e) {
            result.addError("Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error validating OpenAPI spec", e);
            result.addError("Validation error: " + e.getMessage());
        }

        return result;
    }

    @Override
    public ParsedOpenApiSpec parse(String openApiJson) {
        OpenApiValidationResult validationResult = validate(openApiJson);
        if (!validationResult.isValid()) {
            throw new ValidationException("OpenAPI specification validation failed", 
                    validationResult.getErrors());
        }
        return validationResult.getParsedSpec();
    }

    private void validateOpenApiStructure(JsonNode spec, OpenApiValidationResult result) {
        // Check required root fields
        if (!spec.has("openapi")) {
            result.addError("Missing required field: openapi");
            return;
        }

        String version = spec.get("openapi").asText();
        if (!version.startsWith("3.0") && !version.startsWith("3.1")) {
            result.addError("Unsupported OpenAPI version: " + version + 
                    ". Only 3.0.x and 3.1.x are supported");
            return;
        }

        // Validate info object
        if (!spec.has("info")) {
            result.addError("Missing required field: info");
            return;
        }
        
        JsonNode info = spec.get("info");
        if (!info.has("title")) {
            result.addError("info object must contain title");
        }
        if (!info.has("version")) {
            result.addError("info object must contain version");
        }

        // Validate servers
        if (!spec.has("servers") || !spec.get("servers").isArray() || 
                spec.get("servers").size() == 0) {
            result.addError("At least one server must be defined");
            return;
        }

        // Validate server URLs are HTTP or HTTPS
        JsonNode servers = spec.get("servers");
        for (JsonNode server : servers) {
            if (!server.has("url")) {
                result.addError("Server must have a URL");
                continue;
            }
            String url = server.get("url").asText();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                result.addError("Server URLs must use HTTP or HTTPS protocol: " + url);
            }
        }

        // Validate paths
        if (!spec.has("paths")) {
            result.addError("Missing required field: paths");
            return;
        }
        
        JsonNode paths = spec.get("paths");
        if (paths.size() == 0) {
            result.addError("At least one path must be defined");
        }
    }

    private void validateBusinessRules(OpenAPI openAPI, OpenApiValidationResult result) {
        // Validate endpoint count
        int endpointCount = countEndpoints(openAPI);
        if (endpointCount > maxEndpoints) {
            result.addError(String.format("Too many endpoints (%d). Maximum allowed is %d", 
                    endpointCount, maxEndpoints));
        }

        // Validate schema references if present
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            validateSchemaReferences(openAPI, result);
        }
    }

    private int countEndpoints(OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return 0;
        }

        return openAPI.getPaths().values().stream()
                .mapToInt(pathItem -> pathItem.readOperationsMap().size())
                .sum();
    }

    private void validateSchemaReferences(OpenAPI openAPI, OpenApiValidationResult result) {
        // This is a simplified validation - Swagger Parser already resolves most references
        // Just add a warning if there are complex schemas
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            int schemaCount = openAPI.getComponents().getSchemas().size();
            if (schemaCount > 50) {
                result.addWarning(String.format("Large number of schemas detected (%d). " +
                        "Consider splitting into multiple specifications.", schemaCount));
            }
        }
    }

    private ParsedOpenApiSpec extractMetadata(OpenAPI openAPI, String originalJson) {
        ParsedOpenApiSpec.ParsedOpenApiSpecBuilder builder = ParsedOpenApiSpec.builder();

        // Extract basic info
        if (openAPI.getInfo() != null) {
            builder.title(openAPI.getInfo().getTitle())
                    .version(openAPI.getInfo().getVersion())
                    .description(openAPI.getInfo().getDescription());
        }

        // Extract OpenAPI version
        builder.openapiVersion(openAPI.getOpenapi());

        // Extract base URL (first server)
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            builder.baseUrl(openAPI.getServers().get(0).getUrl());
        }

        // Extract global tags
        if (openAPI.getTags() != null) {
            List<String> tags = openAPI.getTags().stream()
                    .map(tag -> tag.getName())
                    .collect(Collectors.toList());
            builder.tags(tags);
        }

        // Extract endpoints
        List<ParsedOpenApiSpec.ParsedEndpoint> endpoints = new ArrayList<>();
        if (openAPI.getPaths() != null) {
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();

                for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : 
                        pathItem.readOperationsMap().entrySet()) {
                    
                    ParsedOpenApiSpec.ParsedEndpoint endpoint = extractEndpoint(
                            path, 
                            operationEntry.getKey().name(), 
                            operationEntry.getValue()
                    );
                    endpoints.add(endpoint);
                }
            }
        }
        builder.endpoints(endpoints);

        return builder.build();
    }

    private ParsedOpenApiSpec.ParsedEndpoint extractEndpoint(String path, String method, Operation operation) {
        ParsedOpenApiSpec.ParsedEndpoint.ParsedEndpointBuilder builder = 
                ParsedOpenApiSpec.ParsedEndpoint.builder();

        builder.path(path)
                .method(method.toUpperCase())
                .operationId(operation.getOperationId())
                .summary(operation.getSummary())
                .description(operation.getDescription());

        // Extract parameters
        if (operation.getParameters() != null && !operation.getParameters().isEmpty()) {
            try {
                builder.parametersJson(objectMapper.writeValueAsString(operation.getParameters()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize parameters for {}", path, e);
            }
        }

        // Extract request body
        if (operation.getRequestBody() != null) {
            try {
                builder.requestBodyJson(objectMapper.writeValueAsString(operation.getRequestBody()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize request body for {}", path, e);
            }
        }

        // Extract responses
        if (operation.getResponses() != null) {
            try {
                builder.responsesJson(objectMapper.writeValueAsString(operation.getResponses()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize responses for {}", path, e);
            }
        }

        // Extract security requirements
        if (operation.getSecurity() != null) {
            try {
                builder.securityJson(objectMapper.writeValueAsString(operation.getSecurity()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize security for {}", path, e);
            }
        }

        // Extract tags
        if (operation.getTags() != null) {
            builder.tags(operation.getTags());
        }

        return builder.build();
    }
}
