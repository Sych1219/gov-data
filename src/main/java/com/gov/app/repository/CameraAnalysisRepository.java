package com.gov.app.repository;

import com.gov.app.domain.CameraAnalysis;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CameraAnalysisRepository extends ReactiveCrudRepository<CameraAnalysis, Long> {

    Mono<CameraAnalysis> findByCameraId(Long cameraId);

    @Modifying
    @Query("""
        INSERT INTO camera_analysis
            (camera_id, snapshot_id, congestion, vehicle_density,
             incidents, weather, road_surface, summary, analyzed_at)
        VALUES
            (:cameraId, :snapshotId, :congestion, :vehicleDensity,
             :incidents, :weather, :roadSurface, :summary, NOW())
        ON CONFLICT (camera_id) DO UPDATE SET
            snapshot_id      = EXCLUDED.snapshot_id,
            congestion       = EXCLUDED.congestion,
            vehicle_density  = EXCLUDED.vehicle_density,
            incidents        = EXCLUDED.incidents,
            weather          = EXCLUDED.weather,
            road_surface     = EXCLUDED.road_surface,
            summary          = EXCLUDED.summary,
            analyzed_at      = EXCLUDED.analyzed_at
        """)
    Mono<Void> upsert(Long cameraId, Long snapshotId, String congestion, String vehicleDensity,
                      String incidents, String weather, String roadSurface, String summary);

    @Query("SELECT * FROM camera_analysis WHERE camera_id = ANY(:cameraIds)")
    Flux<CameraAnalysis> findByCameraIds(Long[] cameraIds);
}
