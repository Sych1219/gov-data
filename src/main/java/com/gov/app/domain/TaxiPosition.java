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
@Table(name = "taxi_positions")
public class TaxiPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "api_timestamp")
    private OffsetDateTime apiTimestamp;

    private double longitude;

    private double latitude;

    // NOTE: The 'geog' GEOGRAPHY column is a generated column in PostgreSQL.
    // It is intentionally excluded from this entity — JPA must never write to it.
}
