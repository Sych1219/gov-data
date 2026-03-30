package com.gov.app.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("cameras")
public class Camera {

    @Id
    private Long id;
    
    @Column("camera_id")
    private Long cameraId;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column("location_name")
    private String locationName;

    private String expressway;

    private String resolution;

    @Column("first_seen_at")
    private OffsetDateTime firstSeenAt;

    @Column("last_seen_at")
    private OffsetDateTime lastSeenAt;
}
