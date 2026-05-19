package com.gov.app.service;

import com.gov.app.domain.TaxiSnapshot;
import com.gov.app.dto.upstream.GovTaxiResponse;
import com.gov.app.repository.TaxiPositionRepository;
import com.gov.app.repository.TaxiSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxiFetchService {

    private static final String TAXI_PATH = "/v1/transport/taxi-availability";

    private final RestClient taxiWebClient;
    private final TaxiSnapshotRepository snapshotRepository;
    private final TaxiPositionRepository positionRepository;

    private static final int MAX_RETRIES = 3;

    public void fetchAndSave() {
        GovTaxiResponse response = fetchWithRetry();
        if (response == null) return;
        persist(response);
    }

    private GovTaxiResponse fetchWithRetry() {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return taxiWebClient.get()
                        .uri(TAXI_PATH)
                        .retrieve()
                        .body(GovTaxiResponse.class);
            } catch (RestClientException ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES) {
                    long sleepMs = (long) Math.pow(2, attempt) * 1000L;
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Failed to fetch taxi data after {} retries: {}",
                MAX_RETRIES, lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }

    private void persist(GovTaxiResponse response) {
        if (response.getFeatures() == null || response.getFeatures().isEmpty()) {
            log.warn("Upstream returned empty features list — skipping");
            return;
        }

        GovTaxiResponse.Feature feature = response.getFeatures().get(0);
        GovTaxiResponse.Properties props = feature.getProperties();
        GovTaxiResponse.Geometry geometry = feature.getGeometry();

        OffsetDateTime apiTimestamp = OffsetDateTime.parse(props.getTimestamp());

        TaxiSnapshot snapshot = TaxiSnapshot.builder()
                .apiTimestamp(apiTimestamp)
                .taxiCount(props.getTaxiCount())
                .fetchedAt(OffsetDateTime.now())
                .build();

        TaxiSnapshot saved = snapshotRepository.save(snapshot);

        List<Double> lons = new ArrayList<>();
        List<Double> lats = new ArrayList<>();
        for (List<Double> coord : geometry.getCoordinates()) {
            lons.add(coord.get(0)); // longitude first in GeoJSON
            lats.add(coord.get(1));
        }
        log.info("Saved snapshot id={} timestamp={} taxiCount={}",
                saved.getId(), apiTimestamp, props.getTaxiCount());
        positionRepository.batchInsert(saved.getId(), apiTimestamp, lons, lats);
    }
}
