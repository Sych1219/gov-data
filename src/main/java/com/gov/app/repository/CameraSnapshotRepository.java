package com.gov.app.repository;

import com.gov.app.domain.CameraSnapshot;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CameraSnapshotRepository extends ReactiveCrudRepository<CameraSnapshot, Long> {

    Mono<CameraSnapshot> findTopByCameraIdOrderByTimestampDesc(Long cameraId);

    @Query("""
        SELECT cs.* FROM camera_snapshots cs
        INNER JOIN (
            SELECT camera_id, MAX(timestamp) AS max_ts
            FROM camera_snapshots
            GROUP BY camera_id
        ) latest ON cs.camera_id = latest.camera_id AND cs.timestamp = latest.max_ts
        """)
    Flux<CameraSnapshot> findLatestPerCamera();

    @Query("""
        SELECT cs.* FROM camera_snapshots cs
        INNER JOIN (
            SELECT camera_id, MAX(timestamp) AS max_ts
            FROM camera_snapshots
            WHERE camera_id = ANY(:cameraIds)
            GROUP BY camera_id
        ) latest ON cs.camera_id = latest.camera_id AND cs.timestamp = latest.max_ts
        """)
    Flux<CameraSnapshot> findLatestByCameraIds(Long[] cameraIds);
}
