package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages daily partitions for the taxi_positions table.
 * Each partition covers one SGT day:
 *   FROM  (sgtDate - 1 day) 16:00:00 UTC
 *   TO    sgtDate            16:00:00 UTC
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PartitionRepository {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter PARTITION_NAME_FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final DatabaseClient db;

    /**
     * Ensures partitions exist for today and the next {@code days - 1} SGT days.
     * Uses IF NOT EXISTS logic via pg_class lookup to avoid errors on re-runs.
     */
    public Mono<Void> ensurePartitionsExist(int days) {
        LocalDate todaySgt = ZonedDateTime.now(SGT).toLocalDate();
        Mono<Void> chain = Mono.empty();
        for (int i = 0; i < days; i++) {
            LocalDate sgtDate = todaySgt.plusDays(i);
            chain = chain.then(createPartitionIfNotExists(sgtDate));
        }
        return chain;
    }

    /**
     * Creates a partition for the given SGT date if it does not already exist.
     * SGT day boundaries in UTC:
     *   FROM  (sgtDate - 1 day) 16:00:00+00
     *   TO    sgtDate            16:00:00+00
     */
    private Mono<Void> createPartitionIfNotExists(LocalDate sgtDate) {
        String partitionName = "taxi_positions_" + sgtDate.format(PARTITION_NAME_FMT);
        String fromUtc = sgtDate.minusDays(1) + "T16:00:00+00";
        String toUtc = sgtDate + "T16:00:00+00";

        // Check if partition already exists in pg_class
        return db.sql("SELECT 1 FROM pg_class WHERE relname = :name")
                .bind("name", partitionName)
                .fetch().one()
                .flatMap(row -> {
                    log.debug("Partition {} already exists", partitionName);
                    return Mono.<Void>empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Partition name is safe — derived from LocalDate format (digits + underscores only)
                    String sql = String.format(
                            "CREATE TABLE %s PARTITION OF taxi_positions FOR VALUES FROM ('%s') TO ('%s')",
                            partitionName, fromUtc, toUtc);
                    log.info("Creating partition: {} [{} to {})", partitionName, fromUtc, toUtc);
                    return db.sql(sql)
                            .fetch()
                            .rowsUpdated()
                            .then();
                }));
    }
}
