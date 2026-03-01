package com.gov.app.service;

import com.gov.app.domain.TaxiSnapshot;
import com.gov.app.dto.upstream.GovTaxiResponse;
import com.gov.app.exception.UpstreamException;
import com.gov.app.repository.TaxiPositionRepository;
import com.gov.app.repository.TaxiSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxiFetchService {

    private static final String TAXI_PATH = "/v1/transport/taxi-availability";

    private final WebClient taxiWebClient;
    private final TaxiSnapshotRepository snapshotRepository;
    private final TaxiPositionRepository positionRepository;

    /**
     * Fetches the latest taxi availability from data.gov.sg, persists a snapshot,
     * and batch-inserts all taxi positions.
     */
    public Mono<Void> fetchAndSave() {
        return taxiWebClient.get()
                .uri(TAXI_PATH)
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new UpstreamException(
                                        "Upstream taxi API returned " + resp.statusCode() + ": " + body,
                                        resp.statusCode().value()))
                )
                .bodyToMono(GovTaxiResponse.class)
                .flatMap(this::persist)
                .doOnError(ex -> log.error("Failed to fetch taxi data: {}", ex.getMessage()));
    }

    private Mono<Void> persist(GovTaxiResponse response) {
        if (response.getFeatures() == null || response.getFeatures().isEmpty()) {
            log.warn("Upstream returned empty features list — skipping");
            return Mono.empty();
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

        return snapshotRepository.save(snapshot)
                .flatMap(saved -> {
                    List<Double> lons = new ArrayList<>();
                    List<Double> lats = new ArrayList<>();
                    for (List<Double> coord : geometry.getCoordinates()) {
                        lons.add(coord.get(0)); // longitude first in GeoJSON
                        lats.add(coord.get(1));
                    }
                    log.info("Saved snapshot id={} timestamp={} taxiCount={}",
                            saved.getId(), apiTimestamp, props.getTaxiCount());
                    return positionRepository.batchInsert(saved.getId(), lons, lats);
                });
    }
}
