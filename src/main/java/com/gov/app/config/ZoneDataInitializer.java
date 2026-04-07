package com.gov.app.config;

import com.gov.app.service.ZoneSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once at startup to populate the zones table if it is empty.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ZoneDataInitializer implements ApplicationRunner {

    private final ZoneSeedService zoneSeedService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            zoneSeedService.seedIfEmpty();
        } catch (Exception ex) {
            log.error("zone seed failed, zone queries may return 0", ex);
        }
    }
}
