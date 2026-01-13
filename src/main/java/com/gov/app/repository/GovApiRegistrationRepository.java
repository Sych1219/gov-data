package com.gov.app.repository;

import com.gov.app.domain.GovApiRegistration;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GovApiRegistrationRepository extends R2dbcRepository<GovApiRegistration, UUID> {

    Mono<Boolean> existsByNameIgnoreCaseAndBaseUrl(String name, String baseUrl);
}
