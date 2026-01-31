package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gov.app.domain.GovOpenApiEndpoint;
import com.gov.app.domain.GovOpenApiRegistration;
import com.gov.app.dto.GovApiTriggerRequestV2;
import com.gov.app.dto.GovApiTriggerResponse;
import com.gov.app.exception.NotFoundException;
import com.gov.app.exception.UpstreamException;
import com.gov.app.exception.ValidationException;
import com.gov.app.repository.GovOpenApiEndpointRepository;
import com.gov.app.repository.GovOpenApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiTriggerServiceImpl implements OpenApiTriggerService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final GovOpenApiEndpointRepository endpointRepository;
    private final GovOpenApiRegistrationRepository registrationRepository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    @Override
    public Mono<GovApiTriggerResponse> trigger(UUID endpointId, GovApiTriggerRequestV2 request, String requestId) {
        log.info("Triggering OpenAPI endpoint. EndpointId: {}, RequestId: {}", endpointId, requestId);

        return loadEndpointContext(endpointId)
                .flatMap(context -> {
                    try {
                        // Parse OpenAPI metadata
                        List<OpenApiParameter> parameters = parseParameters(context.endpoint.getParametersJson());
                        OpenApiRequestBody requestBody = parseRequestBody(context.endpoint.getRequestBodyJson());
                        
                        // Validate runtime parameters
                        Map<String, Object> queryParams = request.getQueryParams() != null ? request.getQueryParams() : Collections.emptyMap();
                        Map<String, Object> bodyParams = request.getBodyParams() != null ? request.getBodyParams() : Collections.emptyMap();
                        
                        validateQueryParameters(queryParams, parameters, context.endpoint.getHttpMethod());
                        validateBodyParameters(bodyParams, requestBody, context.endpoint.getHttpMethod());
                        
                        // Construct outbound request
                        URI uri = buildRequestUri(context.registration.getBaseUrl(), context.endpoint.getPath(), queryParams);
                        String requestBodyJson = buildRequestBody(bodyParams, requestBody);
                        Map<String, String> headers = extractHeaders(context.registration.getOpenapiSpecJson());
                        
                        log.debug("Request params - Query keys: {}, Body keys: {}, RequestId: {}", 
                            queryParams.keySet(), bodyParams.keySet(), requestId);
                        
                        // Execute external API call
                        return invokeExternal(
                            HttpMethod.valueOf(context.endpoint.getHttpMethod().toUpperCase()),
                            uri,
                            headers,
                            requestBodyJson,
                            requestId
                        ).map(result -> buildTriggerResponse(endpointId, result, requestId));
                        
                    } catch (Exception e) {
                        log.error("Failed to trigger endpoint. EndpointId: {}, Error: {}, RequestId: {}", 
                            endpointId, e.getMessage(), requestId);
                        return Mono.error(e);
                    }
                });
    }

    private Mono<EndpointContext> loadEndpointContext(UUID endpointId) {
        return endpointRepository.findById(endpointId)
                .switchIfEmpty(Mono.error(new NotFoundException("Endpoint not found: " + endpointId)))
                .flatMap(endpoint -> registrationRepository.findById(endpoint.getOpenapiId())
                        .switchIfEmpty(Mono.error(new NotFoundException("OpenAPI registration not found: " + endpoint.getOpenapiId())))
                        .map(registration -> new EndpointContext(endpoint, registration))
                );
    }

    private List<OpenApiParameter> parseParameters(String parametersJson) {
        if (parametersJson == null || parametersJson.isBlank()) {
            return Collections.emptyList();
        }
        
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<List<OpenApiParameter>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse parameters JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private OpenApiRequestBody parseRequestBody(String requestBodyJson) {
        if (requestBodyJson == null || requestBodyJson.isBlank()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(requestBodyJson, OpenApiRequestBody.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse request body JSON: {}", e.getMessage());
            return null;
        }
    }

    private void validateQueryParameters(Map<String, Object> queryParams, List<OpenApiParameter> parameterDefs, String httpMethod) {
        // Get query parameters from OpenAPI spec
        List<OpenApiParameter> queryParamDefs = parameterDefs.stream()
                .filter(p -> "query".equals(p.getIn()))
                .collect(Collectors.toList());
        
        // Check for unknown parameters
        Set<String> definedParamNames = queryParamDefs.stream()
                .map(OpenApiParameter::getName)
                .collect(Collectors.toSet());
        
        List<String> errors = new ArrayList<>();
        
        for (String paramName : queryParams.keySet()) {
            if (!definedParamNames.contains(paramName)) {
                errors.add("Unknown query parameter: " + paramName);
            }
        }
        
        // Check for required parameters
        for (OpenApiParameter param : queryParamDefs) {
            if (Boolean.TRUE.equals(param.getRequired()) && !queryParams.containsKey(param.getName())) {
                errors.add("Required query parameter missing: " + param.getName());
            }
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Query parameter validation failed", errors);
        }
    }

    private void validateBodyParameters(Map<String, Object> bodyParams, OpenApiRequestBody requestBody, String httpMethod) {
        // GET and DELETE should not have body
        if (("GET".equals(httpMethod) || "DELETE".equals(httpMethod)) && !bodyParams.isEmpty()) {
            throw new ValidationException("Body parameters not allowed for " + httpMethod + " requests");
        }
        
        if (requestBody == null) {
            if (!bodyParams.isEmpty()) {
                throw new ValidationException("Body parameters not allowed for this endpoint");
            }
            return;
        }
        
        OpenApiSchema schema = requestBody.getContent() != null 
            ? requestBody.getContent().get("application/json") 
            : null;
        
        if (schema == null) {
            if (!bodyParams.isEmpty()) {
                throw new ValidationException("Body parameters not allowed for this endpoint");
            }
            return;
        }
        
        OpenApiObjectSchema objectSchema = schema.getSchema();
        if (objectSchema == null) {
            return;
        }
        
        List<String> errors = new ArrayList<>();
        
        // Check for unknown properties
        if (objectSchema.getProperties() != null) {
            Set<String> definedPropertyNames = objectSchema.getProperties().keySet();
            
            for (String propertyName : bodyParams.keySet()) {
                if (!definedPropertyNames.contains(propertyName)) {
                    errors.add("Unknown body property: " + propertyName);
                }
            }
        }
        
        // Check for required properties
        if (objectSchema.getRequired() != null) {
            for (String requiredProp : objectSchema.getRequired()) {
                if (!bodyParams.containsKey(requiredProp)) {
                    errors.add("Required body property missing: " + requiredProp);
                }
            }
        }
        
        if (!errors.isEmpty()) {
            throw new ValidationException("Body parameter validation failed", errors);
        }
    }

    private URI buildRequestUri(String baseUrl, String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        
        return builder.build().toUri();
    }

    private String buildRequestBody(Map<String, Object> bodyParams, OpenApiRequestBody requestBody) {
        if (bodyParams.isEmpty() || requestBody == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(bodyParams);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Failed to serialize request body: " + e.getMessage());
        }
    }

    private Map<String, String> extractHeaders(String openapiSpecJson) {
        // Extract headers from OpenAPI spec if needed
        // For now, return basic headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private Mono<UpstreamCallResult> invokeExternal(HttpMethod method, URI uri, Map<String, String> headers, 
                                                     String requestBody, String requestId) {
        long startTime = System.currentTimeMillis();
        
        log.info("Calling external API. Method: {}, URI: {}, RequestId: {}", method, uri, requestId);
        
        WebClient webClient = webClientBuilder
                .defaultHeaders(h -> headers.forEach(h::set))
                .build();
        
        WebClient.RequestBodySpec requestSpec = webClient.method(method)
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON);
        
        if (requestBody != null && (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH)) {
            requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody);
        }
        
        return requestSpec
                .retrieve()
                .onStatus(
                    status -> status.is4xxClientError() || status.is5xxServerError(),
                    response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            String errorMsg = String.format("External API returned %d: %s", 
                                response.statusCode().value(), body);
                            return Mono.error(new UpstreamException(errorMsg, response.statusCode().value()));
                        })
                )
                .bodyToMono(String.class)
                .timeout(DEFAULT_TIMEOUT)
                .map(body -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("External API call completed. Status: 200, Duration: {}ms, RequestId: {}", 
                        duration, requestId);
                    
                    // Enforce response size limit
                    int bodyBytes = body.getBytes(StandardCharsets.UTF_8).length;
                    if (bodyBytes > MAX_RESPONSE_BYTES) {
                        log.warn("Response size ({} bytes) exceeds limit ({} bytes), truncating. RequestId: {}", 
                            bodyBytes, MAX_RESPONSE_BYTES, requestId);
                        body = body.substring(0, MAX_RESPONSE_BYTES / 2) + "... [truncated]";
                    }
                    
                    return new UpstreamCallResult(200, body);
                })
                .onErrorResume(UpstreamException.class, e -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("External API call failed. Status: {}, Duration: {}ms, RequestId: {}", 
                        e.getStatusCode(), duration, requestId);
                    return Mono.error(e);
                })
                .onErrorMap(throwable -> {
                    if (!(throwable instanceof UpstreamException)) {
                        log.error("External API call failed with exception. RequestId: {}, Error: {}", 
                            requestId, throwable.getMessage());
                        return new UpstreamException("Failed to call external API: " + throwable.getMessage(), 0);
                    }
                    return throwable;
                });
    }

    private GovApiTriggerResponse buildTriggerResponse(UUID endpointId, UpstreamCallResult result, String requestId) {
        JsonNode responseBody = parseResponseBody(result.getBody());
        
        return GovApiTriggerResponse.builder()
                .status("SUCCESS")
                .endpointId(endpointId)
                .externalStatus(result.getStatusCode())
                .invokedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .requestId(requestId)
                .responseBody(responseBody)
                .build();
    }

    private JsonNode parseResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return JsonNodeFactory.instance.nullNode();
        }
        
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse response body as JSON, returning as text: {}", e.getMessage());
            return JsonNodeFactory.instance.textNode(body);
        }
    }

    // Helper classes
    private static class EndpointContext {
        private final GovOpenApiEndpoint endpoint;
        private final GovOpenApiRegistration registration;

        public EndpointContext(GovOpenApiEndpoint endpoint, GovOpenApiRegistration registration) {
            this.endpoint = endpoint;
            this.registration = registration;
        }
    }

    private static class UpstreamCallResult {
        private final int statusCode;
        private final String body;

        public UpstreamCallResult(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }

    // OpenAPI schema classes for parsing
    @lombok.Data
    private static class OpenApiParameter {
        private String name;
        private String in;  // query, header, path, cookie
        private Boolean required;
        private OpenApiSchema schema;
    }

    @lombok.Data
    private static class OpenApiSchema {
        private String type;
        private String format;
        private List<String> enumValues;
        private OpenApiObjectSchema schema;  // For request body content
    }

    @lombok.Data
    private static class OpenApiRequestBody {
        private Boolean required;
        private Map<String, OpenApiSchema> content;  // e.g., "application/json" -> schema
    }

    @lombok.Data
    private static class OpenApiObjectSchema {
        private String type;
        private Map<String, OpenApiPropertySchema> properties;
        private List<String> required;
    }

    @lombok.Data
    private static class OpenApiPropertySchema {
        private String type;
        private String format;
        private Object defaultValue;
    }
}
