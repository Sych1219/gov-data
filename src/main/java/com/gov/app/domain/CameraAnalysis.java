package com.gov.app.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("camera_analysis")
public class CameraAnalysis {

    @Id
    private Long id;

    @Column("camera_id")
    private Long cameraId;

    @Column("snapshot_id")
    private Long snapshotId;

    private String congestion;

    @Column("vehicle_density")
    private String vehicleDensity;

    private String incidents;

    private String weather;

    @Column("road_surface")
    private String roadSurface;

    private String summary;

    @Column("analyzed_at")
    private OffsetDateTime analyzedAt;
}
