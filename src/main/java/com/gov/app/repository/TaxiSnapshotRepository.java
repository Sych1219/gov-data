package com.gov.app.repository;

import com.gov.app.domain.TaxiSnapshot;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface TaxiSnapshotRepository extends ReactiveCrudRepository<TaxiSnapshot, Long> {

    /** Latest snapshot overall (no datetime filter). */
    Mono<TaxiSnapshot> findFirstByOrderByApiTimestampDesc();

    /** Latest snapshot at or before the given time. */
    Mono<TaxiSnapshot> findTopByApiTimestampLessThanEqualOrderByApiTimestampDesc(OffsetDateTime time);

    /** All snapshots within a time window, oldest first. */
    Flux<TaxiSnapshot> findByApiTimestampBetweenOrderByApiTimestampAsc(OffsetDateTime start, OffsetDateTime end);
}
