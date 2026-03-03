package com.gov.app.config;

import com.gov.app.service.ZoneSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Runs once at startup to populate the zones table if it is empty.
 * Blocking is acceptable here because this is called on the main thread
 * before the application begins serving traffic.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ZoneDataInitializer implements ApplicationRunner {

    private final ZoneSeedService zoneSeedService;

    @Override
    public void run(ApplicationArguments args) {
        zoneSeedService.seedIfEmpty()
                .doOnError(ex -> log.error("zone seed failed, zone queries may return 0", ex))
                .onErrorResume(ex -> Mono.empty())   // don't prevent app startup
                .block(Duration.ofSeconds(30));
    }
}
