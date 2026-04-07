package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    private final JdbcTemplate jdbc;

    /**
     * Ensures partitions exist for today and the next {@code days - 1} SGT days.
     */
    public void ensurePartitionsExist(int days) {
        LocalDate todaySgt = ZonedDateTime.now(SGT).toLocalDate();
        for (int i = 0; i < days; i++) {
            createPartitionIfNotExists(todaySgt.plusDays(i));
        }
    }

    /**
     * Creates a partition for the given SGT date if it does not already exist.
     * SGT day boundaries in UTC:
     *   FROM  (sgtDate - 1 day) 16:00:00+00
     *   TO    sgtDate            16:00:00+00
     */
    private void createPartitionIfNotExists(LocalDate sgtDate) {
        String partitionName = "taxi_positions_" + sgtDate.format(PARTITION_NAME_FMT);
        String fromUtc = sgtDate.minusDays(1) + "T16:00:00+00";
        String toUtc = sgtDate + "T16:00:00+00";

        List<Integer> existing = jdbc.query(
                "SELECT 1 FROM pg_class WHERE relname = ?",
                (rs, i) -> 1,
                partitionName);

        if (!existing.isEmpty()) {
            log.debug("Partition {} already exists", partitionName);
            return;
        }

        // Partition name is safe — derived from LocalDate format (digits + underscores only)
        String sql = String.format(
                "CREATE TABLE %s PARTITION OF taxi_positions FOR VALUES FROM ('%s') TO ('%s')",
                partitionName, fromUtc, toUtc);
        log.info("Creating partition: {} [{} to {})", partitionName, fromUtc, toUtc);
        jdbc.execute(sql);
    }
}
