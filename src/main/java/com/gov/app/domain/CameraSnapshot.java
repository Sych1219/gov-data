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
@Table("camera_snapshots")
public class CameraSnapshot {

    @Id
    private Long id;

    @Column("camera_id")
    private Long cameraId;

    private OffsetDateTime timestamp;

    @Column("image_url")
    private String imageUrl;

    @Column("image_md5")
    private String imageMd5;

    @Column("image_width")
    private Integer imageWidth;

    @Column("image_height")
    private Integer imageHeight;

    @Column("created_at")
    private OffsetDateTime createdAt;
}
