package com.gov.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gov.app.config.CorrelationIdFilter;
import com.gov.app.dto.GovApiTriggerRequestV2;
import com.gov.app.dto.GovApiTriggerResponse;
import com.gov.app.dto.OpenApiRegistrationResponse;
import com.gov.app.exception.ValidationException;
import com.gov.app.service.OpenApiRegistrationService;
import com.gov.app.service.OpenApiTriggerService;
import com.gov.app.service.OpenApiValidationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/gov/apis")
@Validated
@Slf4j
@RequiredArgsConstructor
public class OpenApiRegistrationController {

    private final OpenApiRegistrationService registrationService;
    private final OpenApiValidationService validationService;
    private final OpenApiTriggerService triggerService;

    @PostMapping(value = "/openapi", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OpenApiRegistrationResponse> registerOpenApi(
            @RequestBody @NotNull JsonNode openApiSpec,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        log.info("Received OpenAPI registration request. RequestId: {}", requestId);

        // Validate and parse specification
        String openApiJson = openApiSpec.toString();
        
        return Mono.fromCallable(() -> validationService.validate(openApiJson))
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new ValidationException(
                                "OpenAPI specification validation failed",
                                validationResult.getErrors()));
                    }

                    // Parse the validated specification
                    return Mono.fromCallable(() -> validationService.parse(openApiJson));
                })
                .flatMap(parsedSpec -> {
                    // Register the API with parsed data
                    return registrationService.register(parsedSpec, openApiJson);
                });
    }

    @GetMapping("/openapi/{id}")
    public Mono<OpenApiRegistrationResponse> getOpenApi(@PathVariable UUID id) {
        log.info("Retrieving OpenAPI registration with id: {}", id);
        return registrationService.getById(id);
    }

    /**
     * V2 Trigger endpoint - Simplified parameter structure for OpenAPI endpoints
     * POST /api/v2/gov/apis/endpoints/{endpointId}/trigger
     */
    @Operation(summary = "Trigger registered OpenAPI endpoint with simplified parameters")
    @PostMapping(
        path = "/endpoints/{endpointId}/trigger",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<GovApiTriggerResponse>> triggerEndpoint(
            @PathVariable("endpointId") UUID endpointId,
            @Valid @RequestBody GovApiTriggerRequestV2 request,
            ServerWebExchange exchange) {

        String requestId = resolveRequestId(exchange);
        
        log.info("Triggering OpenAPI endpoint V2. EndpointId: {}, RequestId: {}", endpointId, requestId);

        return triggerService.trigger(endpointId, request, requestId)
                .map(ResponseEntity::ok);
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(CorrelationIdFilter.HEADER);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }
}
