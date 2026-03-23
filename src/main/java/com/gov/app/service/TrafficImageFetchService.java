package com.gov.app.service;

import com.gov.app.config.ExpresswayMapping;
import com.gov.app.domain.Camera;
import com.gov.app.domain.CameraSnapshot;
import com.gov.app.dto.upstream.GovTrafficImageResponse;
import com.gov.app.exception.UpstreamException;
import com.gov.app.repository.CameraRepository;
import com.gov.app.repository.CameraSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageFetchService {

    private static final String TRAFFIC_IMAGES_PATH = "/v1/transport/traffic-images";

    private final WebClient trafficImageWebClient;
    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;
    private final ExpresswayMapping expresswayMapping;

    public Mono<Void> fetchAndSave() {
        return trafficImageWebClient.get()
                .uri(TRAFFIC_IMAGES_PATH)
                .retrieve()
                .onStatus(
                        status -> !status.is2xxSuccessful(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new UpstreamException(
                                        "Traffic image API returned " + resp.statusCode() + ": " + body,
                                        resp.statusCode().value()))
                )
                .bodyToMono(GovTrafficImageResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(4)))
                .flatMap(this::persist)
                .doOnError(ex -> log.error("Failed to fetch traffic images: {}", ex.getMessage()));
    }

    private Mono<Void> persist(GovTrafficImageResponse response) {
        if (response.getItems() == null || response.getItems().isEmpty()) {
            log.warn("Traffic image API returned empty items — skipping");
            return Mono.empty();
        }

        GovTrafficImageResponse.Item item = response.getItems().get(0);
        if (item.getCameras() == null || item.getCameras().isEmpty()) {
            log.warn("Traffic image API returned empty cameras — skipping");
            return Mono.empty();
        }

        OffsetDateTime now = OffsetDateTime.now();

        return Flux.fromIterable(item.getCameras())
                .flatMap(cam -> upsertCamera(cam, now)
                        .then(upsertSnapshot(cam))
                        .onErrorResume(ex -> {
                            log.warn("Failed to persist camera {}: {}", cam.getCameraId(), ex);
                            return Mono.empty();
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("Ingested {} cameras", item.getCameras().size()));
    }

    private Mono<Void> upsertCamera(GovTrafficImageResponse.Camera cam, OffsetDateTime now) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        return cameraRepository.findByCameraId(cameraId)
                .flatMap(existing -> {
                    existing.setLatitude(BigDecimal.valueOf(cam.getLocation().getLatitude()));
                    existing.setLongitude(BigDecimal.valueOf(cam.getLocation().getLongitude()));
                    existing.setLastSeenAt(now);
                    if (cam.getImageMetadata() != null) {
                        existing.setResolution(resolveResolution(cam.getImageMetadata().getWidth()));
                    }
                    return cameraRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    String expressway = expresswayMapping.resolveExpressway(cameraId);
                    Camera camera = Camera.builder()
                            .cameraId(cameraId)
                            .latitude(BigDecimal.valueOf(cam.getLocation().getLatitude()))
                            .longitude(BigDecimal.valueOf(cam.getLocation().getLongitude()))
                            .expressway(expressway)
                            .resolution(cam.getImageMetadata() != null
                                    ? resolveResolution(cam.getImageMetadata().getWidth()) : null)
                            .firstSeenAt(now)
                            .lastSeenAt(now)
                            .build();
                    return cameraRepository.save(camera);
                }))
                .then();
    }

    private Mono<Void> upsertSnapshot(GovTrafficImageResponse.Camera cam) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        OffsetDateTime ts = OffsetDateTime.parse(cam.getTimestamp());
        String md5 = cam.getImageMetadata() != null ? cam.getImageMetadata().getMd5() : "";

        return snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId)
                .flatMap(existing -> {
                    if (existing.getImageMd5().equals(md5) && existing.getTimestamp().isEqual(ts)) {
                        return Mono.<CameraSnapshot>empty();
                    }
                    existing.setTimestamp(ts);
                    existing.setImageUrl(cam.getImage());
                    existing.setImageMd5(md5);
                    if (cam.getImageMetadata() != null) {
                        existing.setImageWidth(cam.getImageMetadata().getWidth());
                        existing.setImageHeight(cam.getImageMetadata().getHeight());
                    }
                    existing.setCreatedAt(OffsetDateTime.now());
                    return snapshotRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CameraSnapshot snapshot = CameraSnapshot.builder()
                            .cameraId(cameraId)
                            .timestamp(ts)
                            .imageUrl(cam.getImage())
                            .imageMd5(md5)
                            .imageWidth(cam.getImageMetadata() != null ? cam.getImageMetadata().getWidth() : null)
                            .imageHeight(cam.getImageMetadata() != null ? cam.getImageMetadata().getHeight() : null)
                            .createdAt(OffsetDateTime.now())
                            .build();
                    return snapshotRepository.save(snapshot);
                }))
                .then();
    }

    private String resolveResolution(int width) {
        return width >= 1920 ? "HD" : "SD";
    }
}
