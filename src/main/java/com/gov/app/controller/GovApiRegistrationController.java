package com.gov.app.controller;

import com.gov.app.dto.GovApiRegistrationRequest;
import com.gov.app.dto.GovApiRegistrationResponse;
import com.gov.app.service.GovApiRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/gov/apis", produces = MediaType.APPLICATION_JSON_VALUE)
public class GovApiRegistrationController {

    private final GovApiRegistrationService registrationService;

    @Operation(summary = "Register government public API")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<GovApiRegistrationResponse>> register(@Valid @RequestBody GovApiRegistrationRequest request) {
        return Mono.fromCallable(() -> registrationService.register(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }
}
