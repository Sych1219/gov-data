package com.gov.app.controller;

import com.gov.app.config.CorrelationIdFilter;
import com.gov.app.dto.GovApiListResponse;
import com.gov.app.dto.GovApiRegistrationRequest;
import com.gov.app.dto.GovApiRegistrationResponse;
import com.gov.app.dto.GovApiTriggerRequest;
import com.gov.app.dto.GovApiTriggerResponse;
import com.gov.app.service.GovApiRegistrationService;
import com.gov.app.service.GovApiQueryService;
import com.gov.app.service.GovApiTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping(path = "/api/v1/gov/apis", produces = MediaType.APPLICATION_JSON_VALUE)
public class GovApiRegistrationController {

    private final GovApiRegistrationService registrationService;
    private final GovApiQueryService queryService;
    private final GovApiTriggerService triggerService;

    @Operation(summary = "Register government public API")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GovApiRegistrationResponse>> register(@Valid @RequestBody GovApiRegistrationRequest request) {
        return Mono.fromCallable(() -> registrationService.register(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    @Operation(summary = "Trigger registered government public API")
    @PostMapping(path = "/{apiId}/trigger", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GovApiTriggerResponse>> trigger(@PathVariable UUID apiId,
                                                               @Valid @RequestBody GovApiTriggerRequest request,
                                                               ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String requestId = resolveRequestId(exchange);
                    return triggerService.trigger(apiId, request, requestId);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "List or search registered government public APIs")
    @GetMapping
    public Mono<ResponseEntity<GovApiListResponse>> list(@RequestParam(value = "id", required = false) UUID id,
                                                         @RequestParam(value = "description", required = false) String description,
                                                         @RequestParam(value = "page", defaultValue = "0")
                                                         @Min(value = 0, message = "page must be greater than or equal to 0")
                                                         int page,
                                                         @RequestParam(value = "size", defaultValue = "20")
                                                         @Min(value = 1, message = "size must be between 1 and 100")
                                                         @Max(value = 100, message = "size must be between 1 and 100")
                                                         int size,
                                                         @RequestParam(value = "sort", required = false) String sort,
                                                         ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    Map<String, String> filters = extractFilterParams(exchange);
                    return queryService.search(id, description, page, size, sort, filters);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(CorrelationIdFilter.HEADER);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }

    private Map<String, String> extractFilterParams(ServerWebExchange exchange) {
        Map<String, String> filters = new LinkedHashMap<>();
        exchange.getRequest().getQueryParams().forEach((key, values) -> {
            if (key.startsWith("filters[") && key.endsWith("]") && values != null && !values.isEmpty()) {
                String normalizedKey = key.substring(8, key.length() - 1);
                if (!normalizedKey.isBlank()) {
                    filters.put(normalizedKey, values.get(0));
                }
            }
        });
        return filters;
    }
}
