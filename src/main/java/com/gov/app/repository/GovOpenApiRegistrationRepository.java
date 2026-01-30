package com.gov.app.repository;

import com.gov.app.domain.GovOpenApiRegistration;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface GovOpenApiRegistrationRepository extends ReactiveCrudRepository<GovOpenApiRegistration, UUID> {

    @Query("SELECT EXISTS(SELECT 1 FROM gov_openapi_registration WHERE title = :title AND base_url = :baseUrl)")
    Mono<Boolean> existsByTitleAndBaseUrl(String title, String baseUrl);

    Mono<GovOpenApiRegistration> findByTitleAndBaseUrl(String title, String baseUrl);
}
