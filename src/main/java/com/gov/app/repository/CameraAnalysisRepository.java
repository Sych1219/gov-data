package com.gov.app.repository;

import com.gov.app.domain.CameraAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CameraAnalysisRepository extends JpaRepository<CameraAnalysis, Long> {

    Optional<CameraAnalysis> findByCameraId(Long cameraId);

    @Transactional
    @Modifying
    @Query(value = """
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
        """, nativeQuery = true)
    void upsert(Long cameraId, Long snapshotId, String congestion, String vehicleDensity,
                String incidents, String weather, String roadSurface, String summary);

    @Query(value = "SELECT * FROM camera_analysis WHERE camera_id IN (:cameraIds)", nativeQuery = true)
    List<CameraAnalysis> findByCameraIds(List<Long> cameraIds);
}
