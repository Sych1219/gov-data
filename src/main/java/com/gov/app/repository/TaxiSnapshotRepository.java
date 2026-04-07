package com.gov.app.repository;

import com.gov.app.domain.TaxiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TaxiSnapshotRepository extends JpaRepository<TaxiSnapshot, Long> {

    /** Latest snapshot overall (no datetime filter). */
    Optional<TaxiSnapshot> findFirstByOrderByApiTimestampDesc();

    /** Latest snapshot at or before the given time. */
    Optional<TaxiSnapshot> findTopByApiTimestampLessThanEqualOrderByApiTimestampDesc(OffsetDateTime time);

    /** All snapshots within a time window, oldest first. */
    List<TaxiSnapshot> findByApiTimestampBetweenOrderByApiTimestampAsc(OffsetDateTime start, OffsetDateTime end);
}
