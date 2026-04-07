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
@Table(name = "taxi_snapshots")
public class TaxiSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_timestamp")
    private OffsetDateTime apiTimestamp;

    @Column(name = "taxi_count")
    private int taxiCount;

    @Column(name = "fetched_at")
    private OffsetDateTime fetchedAt;
}
