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
@Table("taxi_snapshots")
public class TaxiSnapshot {

    @Id
    private Long id;

    @Column("api_timestamp")
    private OffsetDateTime apiTimestamp;

    @Column("taxi_count")
    private int taxiCount;

    @Column("fetched_at")
    private OffsetDateTime fetchedAt;
}
