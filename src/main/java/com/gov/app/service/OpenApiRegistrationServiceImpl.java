package com.gov.app.service;

import com.gov.app.domain.GovOpenApiEndpoint;
import com.gov.app.domain.GovOpenApiRegistration;
import com.gov.app.dto.OpenApiRegistrationResponse;
import com.gov.app.exception.ConflictException;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.GovOpenApiEndpointRepository;
import com.gov.app.repository.GovOpenApiRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenApiRegistrationServiceImpl implements OpenApiRegistrationService {

    private final GovOpenApiRegistrationRepository registrationRepository;
    private final GovOpenApiEndpointRepository endpointRepository;
    private final OpenApiValidationService validationService;

    @Override
    @Transactional
    public Mono<OpenApiRegistrationResponse> register(String openApiJson) {
        String requestId = UUID.randomUUID().toString();
        log.info("Starting OpenAPI registration. RequestId: {}", requestId);

        return Mono.fromCallable(() -> validationService.parse(openApiJson))
                .flatMap(parsedSpec -> {
                    String title = parsedSpec.getTitle();
                    String baseUrl = parsedSpec.getBaseUrl();

                    log.debug("Parsed OpenAPI spec: {} ({}). RequestId: {}", title, baseUrl, requestId);

                    // Check for duplicates
                    return isAlreadyRegistered(title, baseUrl)
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new ConflictException(
                                            String.format("API with title '%s' and base URL '%s' already exists",
                                                    title, baseUrl)));
                                }

                                // Save registration
                                return saveRegistration(parsedSpec, openApiJson)
                                        .flatMap(registration -> {
                                            log.info("Successfully registered API: {}. ID: {}. RequestId: {}",
                                                    title, registration.getId(), requestId);

                                            // Save endpoints
                                            return saveEndpoints(registration.getId(), parsedSpec)
                                                    .collectList()
                                                    .map(endpoints -> buildResponse(registration, parsedSpec, endpoints));
                                        });
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to register OpenAPI. RequestId: {}. Error: {}",
                            requestId, e.getMessage(), e);
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<Boolean> isAlreadyRegistered(String title, String baseUrl) {
        return registrationRepository.existsByTitleAndBaseUrl(title, baseUrl);
    }

    @Override
    public Mono<OpenApiRegistrationResponse> getById(UUID id) {
        return registrationRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("OpenAPI registration not found with id: " + id)))
                .flatMap(registration -> endpointRepository.findByOpenapiId(id)
                        .collectList()
                        .map(endpoints -> {
                            List<OpenApiRegistrationResponse.EndpointSummary> endpointSummaries =
                                    endpoints.stream()
                                            .map(e -> OpenApiRegistrationResponse.EndpointSummary.builder()
                                                    .path(e.getPath())
                                                    .method(e.getHttpMethod())
                                                    .summary(e.getSummary())
                                                    .build())
                                            .collect(Collectors.toList());

                            return OpenApiRegistrationResponse.builder()
                                    .id(registration.getId())
                                    .title(registration.getTitle())
                                    .version(registration.getVersion())
                                    .baseUrl(registration.getBaseUrl())
                                    .endpointCount(registration.getEndpointCount())
                                    .endpoints(endpointSummaries)
                                    .status("REGISTERED")
                                    .createdAt(registration.getCreatedAt())
                                    .build();
                        }));
    }

    private Mono<GovOpenApiRegistration> saveRegistration(ParsedOpenApiSpec parsedSpec, String openApiJson) {
        GovOpenApiRegistration registration = GovOpenApiRegistration.builder()
                .name(parsedSpec.getTitle()) // Using title as name for simplicity
                .title(parsedSpec.getTitle())
                .version(parsedSpec.getVersion())
                .description(parsedSpec.getDescription())
                .baseUrl(parsedSpec.getBaseUrl())
                .openapiVersion(parsedSpec.getOpenapiVersion())
                .openapiSpecJson(openApiJson)
                .endpointCount(parsedSpec.getEndpoints().size())
                .tags(parsedSpec.getTags())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return registrationRepository.save(registration);
    }

    private Flux<GovOpenApiEndpoint> saveEndpoints(UUID registrationId, ParsedOpenApiSpec parsedSpec) {
        List<GovOpenApiEndpoint> endpoints = parsedSpec.getEndpoints().stream()
                .map(e -> GovOpenApiEndpoint.builder()
                        .openapiId(registrationId)
                        .path(e.getPath())
                        .httpMethod(e.getMethod())
                        .operationId(e.getOperationId())
                        .summary(e.getSummary())
                        .description(e.getDescription())
                        .parametersJson(e.getParametersJson())
                        .requestBodyJson(e.getRequestBodyJson())
                        .responsesJson(e.getResponsesJson())
                        .securityJson(e.getSecurityJson())
                        .tags(e.getTags())
                        .createdAt(Instant.now())
                        .build())
                .collect(Collectors.toList());

        return endpointRepository.saveAll(endpoints);
    }

    private OpenApiRegistrationResponse buildResponse(
            GovOpenApiRegistration registration,
            ParsedOpenApiSpec parsedSpec,
            List<GovOpenApiEndpoint> endpoints) {

        List<OpenApiRegistrationResponse.EndpointSummary> endpointSummaries =
                parsedSpec.getEndpoints().stream()
                        .map(e -> OpenApiRegistrationResponse.EndpointSummary.builder()
                                .path(e.getPath())
                                .method(e.getMethod())
                                .summary(e.getSummary())
                                .build())
                        .collect(Collectors.toList());

        return OpenApiRegistrationResponse.builder()
                .id(registration.getId())
                .title(registration.getTitle())
                .version(registration.getVersion())
                .baseUrl(registration.getBaseUrl())
                .endpointCount(registration.getEndpointCount())
                .endpoints(endpointSummaries)
                .status("REGISTERED")
                .createdAt(registration.getCreatedAt())
                .build();
    }
}
