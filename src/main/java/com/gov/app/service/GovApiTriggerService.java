package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gov.app.config.CorrelationIdFilter;
import com.gov.app.domain.GovApiRegistration;
import com.gov.app.dto.BodyParamType;
import com.gov.app.dto.GovApiTriggerRequest;
import com.gov.app.dto.GovApiTriggerResponse;
import com.gov.app.dto.GovHttpMethod;
import com.gov.app.dto.QueryParamType;
import com.gov.app.exception.ApiRegistrationNotFoundException;
import com.gov.app.exception.UpstreamException;
import com.gov.app.exception.ValidationException;
import com.gov.app.repository.GovApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GovApiTriggerService {

    private static final int MAX_QUERY_BYTES = 8 * 1024;
    private static final int MAX_BODY_BYTES = 32 * 1024;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final GovApiRegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public Mono<GovApiTriggerResponse> trigger(UUID apiId, GovApiTriggerRequest request, String requestId) {
        return repository.findById(apiId)
                .switchIfEmpty(Mono.error(new ApiRegistrationNotFoundException(apiId)))
                .flatMap(registration -> {
                    GovHttpMethod govMethod = GovHttpMethod.valueOf(registration.getHttpMethod());

                    boolean useDefaults = Boolean.TRUE.equals(request.getUseExampleDefaults());
                    Map<String, QueryParamDefinition> queryDefinitions = parseQueryDefinitions(registration.getQueryParamsJson());
                    Map<String, BodyParamDefinition> bodyDefinitions = parseBodyDefinitions(registration.getBodyParamsJson());
                    Map<String, String> storedHeaders = parseHeaders(registration.getHeadersJson());

                    Map<String, Object> runtimeQuery = request.getQuery();
                    Map<String, Object> runtimeBody = request.getBody();

                    if (runtimeQuery != null && queryDefinitions.isEmpty()) {
                        return Mono.error(new ValidationException("Query payload is not allowed for this API"));
                    }
                    if (runtimeBody != null && bodyDefinitions.isEmpty() && requiresBody(govMethod)) {
                        return Mono.error(new ValidationException("Body payload is not allowed for this API"));
                    }
                    if (!requiresBody(govMethod) && runtimeBody != null) {
                        return Mono.error(new ValidationException("Body payload is not allowed for GET registration"));
                    }

                    validateQueryKeys(queryDefinitions, runtimeQuery, "");
                    Map<String, String> queryPayload = buildQueryPayload(queryDefinitions, runtimeQuery, useDefaults);
                    enforcePayloadLimit("query", queryPayload, MAX_QUERY_BYTES);

                    Map<String, Object> bodyPayload = Collections.emptyMap();
                    boolean includeBody = requiresBody(govMethod);
                    if (includeBody) {
                        validateBodyKeys(bodyDefinitions, runtimeBody);
                        bodyPayload = buildBodyPayload(bodyDefinitions, runtimeBody, useDefaults);
                        enforcePayloadLimit("body", bodyPayload, MAX_BODY_BYTES);
                    }

                    Map<String, String> headers = mergeHeaders(storedHeaders, request.getHeaderOverrides(), requestId);

                    return invokeExternal(
                            HttpMethod.valueOf(govMethod.name()),
                            registration.getBaseUrl(),
                            queryPayload,
                            headers,
                            includeBody ? bodyPayload : null
                    ).map(upstreamCallResult -> {
                        JsonNode responseBody = toResponseNode(upstreamCallResult.body());
                        OffsetDateTime invokedAt = OffsetDateTime.now(ZoneOffset.UTC);

                        return GovApiTriggerResponse.builder()
                                .status("SUCCESS")
                                .apiId(apiId)
                                .externalStatus(upstreamCallResult.status())
                                .invokedAt(invokedAt)
                                .requestId(requestId)
                                .responseBody(responseBody)
                                .build();
                    });
                });
    }

    private boolean requiresBody(GovHttpMethod method) {
        return method != GovHttpMethod.GET;
    }

    private Map<String, QueryParamDefinition> parseQueryDefinitions(String json) {
        if (!hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, QueryParamDefinition> definitions = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> definitions.put(entry.getKey(), toQueryDefinition(entry.getValue())));
            return definitions;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored query parameters payload is invalid", e);
        }
    }

    private QueryParamDefinition toQueryDefinition(JsonNode node) {
        QueryParamType type = QueryParamType.valueOf(node.path("type").asText());
        Map<String, QueryParamDefinition> children;
        if (type == QueryParamType.OBJECT && node.has("children")) {
            children = new LinkedHashMap<>();
            node.path("children").fields()
                    .forEachRemaining(entry -> children.put(entry.getKey(), toQueryDefinition(entry.getValue())));
        } else {
            children = Collections.emptyMap();
        }
        String example = node.has("exampleValue") && !node.get("exampleValue").isNull()
                ? node.get("exampleValue").asText()
                : null;
        return new QueryParamDefinition(type, example, children);
    }

    private Map<String, BodyParamDefinition> parseBodyDefinitions(String json) {
        if (!hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, BodyParamDefinition> definitions = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                BodyParamType type = BodyParamType.valueOf(value.path("type").asText());
                String example = value.has("exampleValue") && !value.get("exampleValue").isNull()
                        ? value.get("exampleValue").asText()
                        : null;
                definitions.put(entry.getKey(), new BodyParamDefinition(type, example));
            });
            return definitions;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored body parameters payload is invalid", e);
        }
    }

    private Map<String, String> parseHeaders(String json) {
        if (!hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, String> headers = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode valueNode = entry.getValue().path("value");
                if (!valueNode.isMissingNode() && !valueNode.isNull()) {
                    headers.put(entry.getKey(), valueNode.asText());
                }
            });
            return headers;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored headers payload is invalid", e);
        }
    }

    private void validateQueryKeys(Map<String, QueryParamDefinition> definitions, Map<String, Object> runtime, String path) {
        if (runtime == null) {
            return;
        }
        runtime.forEach((key, value) -> {
            QueryParamDefinition definition = definitions.get(key);
            if (definition == null) {
                throw new ValidationException("Unknown query parameter: " + fullPath(path, key));
            }
            if (definition.type() == QueryParamType.OBJECT) {
                if (!(value instanceof Map<?, ?> valueMap)) {
                    throw new ValidationException("Query parameter " + fullPath(path, key) + " must be an object");
                }
                //noinspection unchecked
                validateQueryKeys(definition.children(), (Map<String, Object>) valueMap, fullPath(path, key));
            } else if (value instanceof Map) {
                throw new ValidationException("Query parameter " + fullPath(path, key) + " must be a scalar value");
            }
        });
    }

    private Map<String, String> buildQueryPayload(Map<String, QueryParamDefinition> definitions,
                                                  Map<String, Object> runtime,
                                                  boolean useDefaults) {
        Map<String, String> resolved = new LinkedHashMap<>();
        definitions.forEach((key, definition) -> {
            Object supplied = runtime != null ? runtime.get(key) : null;
            if (definition.type() == QueryParamType.OBJECT) {
                Map<String, Object> childRuntime = supplied instanceof Map<?, ?> map
                        ? castMap(map)
                        : null;
                resolved.putAll(buildQueryChildren(definition.children(), childRuntime, useDefaults, key));
            } else {
                Object toUse = supplied;
                if (toUse == null && useDefaults) {
                    toUse = definition.example();
                }
                if (toUse != null) {
                    resolved.put(key, convertQueryValue(key, definition.type(), toUse));
                }
            }
        });
        if (runtime != null) {
            runtime.keySet().stream()
                    .filter(key -> !definitions.containsKey(key))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new ValidationException("Unknown query parameter: " + key);
                    });
        }
        return resolved;
    }

    private Map<String, String> buildQueryChildren(Map<String, QueryParamDefinition> children,
                                                   Map<String, Object> runtime,
                                                   boolean useDefaults,
                                                   String prefix) {
        Map<String, String> resolved = new LinkedHashMap<>();
        children.forEach((key, definition) -> {
            Object supplied = runtime != null ? runtime.get(key) : null;
            if (definition.type() == QueryParamType.OBJECT) {
                Map<String, Object> childRuntime = supplied instanceof Map<?, ?> map
                        ? castMap(map)
                        : null;
                resolved.putAll(buildQueryChildren(definition.children(), childRuntime, useDefaults, bracketed(prefix, key)));
            } else {
                Object toUse = supplied;
                if (toUse == null && useDefaults) {
                    toUse = definition.example();
                }
                if (toUse != null) {
                    String value = convertQueryValue(bracketed(prefix, key), definition.type(), toUse);
                    resolved.put(bracketed(prefix, key), value);
                }
            }
        });
        if (runtime != null) {
            runtime.keySet().stream()
                    .filter(key -> !children.containsKey(key))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new ValidationException("Unknown query parameter: " + bracketed(prefix, key));
                    });
        }
        return resolved;
    }

    private String convertQueryValue(String key, QueryParamType type, Object raw) {
        return switch (type) {
            case STRING -> raw.toString();
            case INTEGER -> String.valueOf(toLong(key, raw));
            case FLOAT -> toBigDecimal(key, raw).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(toBoolean(key, raw));
            case OBJECT -> throw new ValidationException("Query parameter " + key + " must not supply an object value");
        };
    }

    private void validateBodyKeys(Map<String, BodyParamDefinition> definitions, Map<String, Object> runtime) {
        if (runtime == null) {
            return;
        }
        runtime.forEach((key, value) -> {
            BodyParamDefinition definition = definitions.get(key);
            if (definition == null) {
                throw new ValidationException("Unknown body parameter: " + key);
            }
            if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
                throw new ValidationException("Body parameter " + key + " must be a scalar value");
            }
        });
    }

    private Map<String, Object> buildBodyPayload(Map<String, BodyParamDefinition> definitions,
                                                 Map<String, Object> runtime,
                                                 boolean useDefaults) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        definitions.forEach((key, definition) -> {
            Object supplied = runtime != null ? runtime.get(key) : null;
            Object toUse = supplied != null ? supplied : (useDefaults ? definition.example() : null);
            if (toUse != null) {
                resolved.put(key, convertBodyValue(key, definition.type(), toUse));
            }
        });
        if (runtime != null) {
            runtime.keySet().stream()
                    .filter(key -> !definitions.containsKey(key))
                    .findFirst()
                    .ifPresent(key -> {
                        throw new ValidationException("Unknown body parameter: " + key);
                    });
        }
        return resolved;
    }

    private Object convertBodyValue(String key, BodyParamType type, Object value) {
        return switch (type) {
            case STRING -> value.toString();
            case INTEGER -> toLong(key, value);
            case FLOAT -> toBigDecimal(key, value);
            case BOOLEAN -> toBoolean(key, value);
        };
    }

    private long toLong(String key, Object value) {
        try {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            throw new ValidationException("Value for " + key + " must be an integer");
        }
    }

    private BigDecimal toBigDecimal(String key, Object value) {
        try {
            if (value instanceof BigDecimal bd) {
                return bd;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            throw new ValidationException("Value for " + key + " must be a number");
        }
    }

    private boolean toBoolean(String key, Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = value.toString().trim().toLowerCase();
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new ValidationException("Value for " + key + " must be a boolean");
    }

    private Map<String, String> mergeHeaders(Map<String, String> stored,
                                             Map<String, String> overrides,
                                             String requestId) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (stored != null) {
            stored.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value != null) {
                    merged.put(key, value);
                }
            });
        }
        merged.put(CorrelationIdFilter.HEADER, requestId);
        return merged;
    }

    private Mono<UpstreamCallResult> invokeExternal(HttpMethod method,
                                                    String baseUrl,
                                                    Map<String, String> query,
                                                    Map<String, String> headers,
                                                    Map<String, Object> body) {
        URI uri = buildUri(baseUrl, query);
        WebClient client = webClientBuilder
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                        .build())
                .build();

        WebClient.RequestBodySpec requestSpec = client
                .method(method)
                .uri(uri)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set));
        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON);
            requestSpec.bodyValue(body);
        }

        return requestSpec.exchangeToMono(clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(payload -> {
                                    enforceResponseLimit(payload);
                                    int status = clientResponse.statusCode().value();
                                    if (clientResponse.statusCode().isError()) {
                                        return Mono.error(new UpstreamException(
                                                "External API returned " + status, status));
                                    }
                                    return Mono.just(new UpstreamCallResult(status, payload));
                                }))
                .timeout(DEFAULT_TIMEOUT)
                .onErrorMap(ex -> ex instanceof UpstreamException
                        ? ex
                        : new UpstreamException("External API request failed: " + ex.getMessage(), 0));
    }

    private URI buildUri(String baseUrl, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl);
        query.forEach(builder::queryParam);
        return builder.build(true).toUri();
    }

    private void enforcePayloadLimit(String name, Object payload, int maxBytes) {
        if (payload == null) {
            return;
        }
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            if (bytes.length > maxBytes) {
                throw new ValidationException(name + " payload exceeds " + maxBytes + " bytes");
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to measure " + name + " payload size", e);
        }
    }

    private void enforceResponseLimit(String payload) {
        int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_RESPONSE_BYTES) {
            throw new UpstreamException("External API response exceeds " + MAX_RESPONSE_BYTES + " bytes", 0);
        }
    }

    private JsonNode toResponseNode(String payload) {
        if (!hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            return JsonNodeFactory.instance.textNode(payload);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> casted = new LinkedHashMap<>();
        map.forEach((key, value) -> casted.put(String.valueOf(key), value));
        return casted;
    }

    private String fullPath(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        return prefix + "." + key;
    }

    private String bracketed(String prefix, String key) {
        if (prefix == null || prefix.isBlank()) {
            return key;
        }
        return prefix + "[" + key + "]";
    }

    private record QueryParamDefinition(QueryParamType type, String example, Map<String, QueryParamDefinition> children) {
    }

    private record BodyParamDefinition(BodyParamType type, String example) {
    }

    private record UpstreamCallResult(int status, String body) {
    }
}
