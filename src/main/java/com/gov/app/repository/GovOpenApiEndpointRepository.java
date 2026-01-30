package com.gov.app.repository;

import com.gov.app.domain.GovOpenApiEndpoint;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface GovOpenApiEndpointRepository extends ReactiveCrudRepository<GovOpenApiEndpoint, UUID> {

    Flux<GovOpenApiEndpoint> findByOpenapiId(UUID openapiId);
}
