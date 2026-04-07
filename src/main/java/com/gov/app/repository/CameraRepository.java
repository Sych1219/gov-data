package com.gov.app.repository;

import com.gov.app.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    Optional<Camera> findByCameraId(Long cameraId);

    List<Camera> findByExpressway(String expressway);

    @Query("SELECT c FROM Camera c WHERE LOWER(c.locationName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Camera> searchByLocationName(String keyword);
}
