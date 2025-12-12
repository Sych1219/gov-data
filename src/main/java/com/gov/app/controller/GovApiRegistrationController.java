package com.gov.app.controller;

import com.gov.app.config.CorrelationIdFilter;
import com.gov.app.dto.GovApiRegistrationRequest;
import com.gov.app.dto.GovApiRegistrationResponse;
import com.gov.app.dto.GovApiTriggerRequest;
import com.gov.app.dto.GovApiTriggerResponse;
import com.gov.app.service.GovApiRegistrationService;
import com.gov.app.service.GovApiTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/gov/apis", produces = MediaType.APPLICATION_JSON_VALUE)
public class GovApiRegistrationController {

    private final GovApiRegistrationService registrationService;
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

    private String resolveRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(CorrelationIdFilter.HEADER);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }
}
