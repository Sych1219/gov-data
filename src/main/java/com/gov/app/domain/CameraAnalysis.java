package com.gov.app.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "camera_analysis")
public class CameraAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id")
    private Long cameraId;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    private String congestion;

    @Column(name = "vehicle_density")
    private String vehicleDensity;

    private String incidents;

    private String weather;

    @Column(name = "road_surface")
    private String roadSurface;

    private String summary;

    @Column(name = "analyzed_at")
    private OffsetDateTime analyzedAt;
}
