package com.gov.app.scheduler;

import com.gov.app.repository.PartitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates daily taxi_positions partitions ahead of time.
 * Each partition covers one SGT day (UTC 16:00 to next-day UTC 16:00).
 *
 * Runs:
 *   1. On application startup — ensures today's + tomorrow's partitions exist.
 *   2. Daily at 15:30 UTC (23:30 SGT) — creates the partition for the next SGT day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionScheduler {

    private final PartitionRepository partitionRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Ensuring taxi_positions partitions exist for today and tomorrow (SGT)");
        partitionRepository.ensurePartitionsExist(2);
    }

    /** Runs daily at 15:30 UTC = 23:30 SGT — 30 minutes before the next SGT day starts. */
    @Scheduled(cron = "0 30 15 * * *", zone = "UTC")
    public void createNextPartition() {
        log.info("Scheduled: creating taxi_positions partition for next SGT day");
        partitionRepository.ensurePartitionsExist(2);
    }
}
