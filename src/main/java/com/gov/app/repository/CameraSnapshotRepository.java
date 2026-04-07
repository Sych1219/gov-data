package com.gov.app.repository;

import com.gov.app.domain.CameraSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CameraSnapshotRepository extends JpaRepository<CameraSnapshot, Long> {

    Optional<CameraSnapshot> findTopByCameraIdOrderByTimestampDesc(Long cameraId);

    @Query(value = """
        SELECT cs.* FROM camera_snapshots cs
        INNER JOIN (
            SELECT camera_id, MAX(timestamp) AS max_ts
            FROM camera_snapshots
            GROUP BY camera_id
        ) latest ON cs.camera_id = latest.camera_id AND cs.timestamp = latest.max_ts
        """, nativeQuery = true)
    List<CameraSnapshot> findLatestPerCamera();

    @Query(value = """
        SELECT cs.* FROM camera_snapshots cs
        INNER JOIN (
            SELECT camera_id, MAX(timestamp) AS max_ts
            FROM camera_snapshots
            WHERE camera_id IN (:cameraIds)
            GROUP BY camera_id
        ) latest ON cs.camera_id = latest.camera_id AND cs.timestamp = latest.max_ts
        """, nativeQuery = true)
    List<CameraSnapshot> findLatestByCameraIds(List<Long> cameraIds);
}
