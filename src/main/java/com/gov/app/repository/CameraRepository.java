package com.gov.app.repository;

import com.gov.app.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    Optional<Camera> findByCameraId(Long cameraId);

    @Query(value = """
            SELECT c.camera_id, c.latitude, c.longitude, c.location_name, c.expressway, c.resolution,
                   cs.image_url, cs.timestamp AS snapshot_timestamp,
                   ca.congestion, ca.vehicle_density, ca.incidents, ca.weather, ca.road_surface, ca.summary, ca.analyzed_at
            FROM cameras c
            LEFT JOIN LATERAL (
                SELECT image_url, timestamp FROM camera_snapshots
                WHERE camera_id = c.camera_id ORDER BY timestamp DESC LIMIT 1
            ) cs ON true
            LEFT JOIN camera_analysis ca ON ca.camera_id = c.camera_id
            """, nativeQuery = true)
    List<CameraDetailProjection> findAllWithDetails();

    @Query(value = """
            SELECT c.camera_id, c.latitude, c.longitude, c.location_name, c.expressway, c.resolution,
                   cs.image_url, cs.timestamp AS snapshot_timestamp,
                   ca.congestion, ca.vehicle_density, ca.incidents, ca.weather, ca.road_surface, ca.summary, ca.analyzed_at
            FROM cameras c
            LEFT JOIN LATERAL (
                SELECT image_url, timestamp FROM camera_snapshots
                WHERE camera_id = c.camera_id ORDER BY timestamp DESC LIMIT 1
            ) cs ON true
            LEFT JOIN camera_analysis ca ON ca.camera_id = c.camera_id
            WHERE c.expressway = :expressway
            """, nativeQuery = true)
    List<CameraDetailProjection> findByExpresswayWithDetails(String expressway);

    @Query(value = """
            SELECT c.camera_id, c.latitude, c.longitude, c.location_name, c.expressway, c.resolution,
                   cs.image_url, cs.timestamp AS snapshot_timestamp,
                   ca.congestion, ca.vehicle_density, ca.incidents, ca.weather, ca.road_surface, ca.summary, ca.analyzed_at
            FROM cameras c
            LEFT JOIN LATERAL (
                SELECT image_url, timestamp FROM camera_snapshots
                WHERE camera_id = c.camera_id ORDER BY timestamp DESC LIMIT 1
            ) cs ON true
            LEFT JOIN camera_analysis ca ON ca.camera_id = c.camera_id
            WHERE LOWER(c.location_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """, nativeQuery = true)
    List<CameraDetailProjection> searchByLocationNameWithDetails(String keyword);

    @Query(value = """
            SELECT c.camera_id, c.latitude, c.longitude, c.location_name, c.expressway, c.resolution,
                   cs.image_url, cs.timestamp AS snapshot_timestamp,
                   ca.congestion, ca.vehicle_density, ca.incidents, ca.weather, ca.road_surface, ca.summary, ca.analyzed_at
            FROM cameras c
            LEFT JOIN LATERAL (
                SELECT image_url, timestamp FROM camera_snapshots
                WHERE camera_id = c.camera_id ORDER BY timestamp DESC LIMIT 1
            ) cs ON true
            LEFT JOIN camera_analysis ca ON ca.camera_id = c.camera_id
            WHERE c.camera_id = :cameraId
            """, nativeQuery = true)
    Optional<CameraDetailProjection> findByIdWithDetails(Long cameraId);

    @Query(value = """
            SELECT c.camera_id, c.latitude, c.longitude, c.location_name, c.expressway, c.resolution,
                   cs.image_url, cs.timestamp AS snapshot_timestamp,
                   ca.congestion, ca.vehicle_density, ca.incidents, ca.weather, ca.road_surface, ca.summary, ca.analyzed_at
            FROM cameras c
            LEFT JOIN LATERAL (
                SELECT image_url, timestamp FROM camera_snapshots
                WHERE camera_id = c.camera_id ORDER BY timestamp DESC LIMIT 1
            ) cs ON true
            LEFT JOIN camera_analysis ca ON ca.camera_id = c.camera_id
            WHERE ST_DWithin(c.geog, ST_MakePoint(:lon, :lat)::geography, :radiusM)
            ORDER BY ST_Distance(c.geog, ST_MakePoint(:lon, :lat)::geography)
            """, nativeQuery = true)
    List<CameraDetailProjection> findNearbyWithDetails(double lat, double lon, int radiusM);
}
