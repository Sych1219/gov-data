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
@Table("taxi_positions")
public class TaxiPosition {

    @Id
    private Long id;

    @Column("snapshot_id")
    private Long snapshotId;

    @Column("api_timestamp")
    private OffsetDateTime apiTimestamp;

    private double longitude;

    private double latitude;

    // NOTE: The 'geog' GEOGRAPHY column is a generated column in PostgreSQL.
    // It is intentionally excluded from this entity — R2DBC must never write to it.
}
