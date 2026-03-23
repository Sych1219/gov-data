package com.gov.app.repository;

import com.gov.app.domain.Camera;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CameraRepository extends ReactiveCrudRepository<Camera, Long> {

    Mono<Camera> findByCameraId(Long cameraId);

    Flux<Camera> findByExpressway(String expressway);

    @Query("SELECT * FROM cameras WHERE LOWER(location_name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Flux<Camera> searchByLocationName(String keyword);
}
