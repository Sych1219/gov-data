package com.gov.app.scheduler;

import com.gov.app.service.TaxiFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaxiDataScheduler {

    private final TaxiFetchService taxiFetchService;

    @Scheduled(fixedDelayString = "${gov-api.taxi.fetch-interval-ms}")
    public void fetchTaxiData() {
        log.debug("Scheduler triggered: fetching taxi data");
        taxiFetchService.fetchAndSave()
                .subscribe(
                        null,
                        ex -> log.error("Taxi fetch failed: {}", ex.getMessage())
                );
    }
}
