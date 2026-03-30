package com.gov.app.scheduler;

import com.gov.app.service.TrafficImageFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrafficImageScheduler {

    private final TrafficImageFetchService trafficImageFetchService;

    @Scheduled(fixedDelayString = "${gov-api.traffic-image.fetch-interval-ms}")
    public void ingestSnapshots() {
        log.debug("Scheduler triggered: fetching traffic images");
        trafficImageFetchService.fetchAndSave()
                .subscribe(
                        null,
                        ex -> log.error("Traffic image fetch failed: {}", ex.getMessage())
                );
    }
}
