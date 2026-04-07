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
@Table(name = "camera_snapshots")
public class CameraSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_id")
    private Long cameraId;

    private OffsetDateTime timestamp;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_md5")
    private String imageMd5;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
