package com.gov.app.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gov.app.dto.OpenApiRegistrationResponse;
import com.gov.app.exception.ValidationException;
import com.gov.app.service.OpenApiRegistrationService;
import com.gov.app.service.OpenApiValidationResult;
import com.gov.app.service.OpenApiValidationService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping(value = "/openapi", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<OpenApiRegistrationResponse> registerOpenApi(
            @RequestBody @NotNull JsonNode openApiSpec,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        log.info("Received OpenAPI registration request. RequestId: {}", requestId);

        // Validate specification
        String openApiJson = openApiSpec.toString();
        
        return Mono.fromCallable(() -> validationService.validate(openApiJson))
                .flatMap(validationResult -> {
                    if (!validationResult.isValid()) {
                        return Mono.error(new ValidationException(
                                "OpenAPI specification validation failed",
                                validationResult.getErrors()));
                    }

                    // Register the API
                    return registrationService.register(openApiJson);
                });
    }

    @GetMapping("/openapi/{id}")
    public Mono<OpenApiRegistrationResponse> getOpenApi(@PathVariable UUID id) {
        log.info("Retrieving OpenAPI registration with id: {}", id);
        return registrationService.getById(id);
    }
}
