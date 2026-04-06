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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageFetchService {

    private static final String TRAFFIC_IMAGES_PATH = "/v1/transport/traffic-images";

    private final WebClient trafficImageWebClient;
    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;
    private final ExpresswayMapping expresswayMapping;
    private final CameraAnalysisService cameraAnalysisService;

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
        AtomicInteger analyzed = new AtomicInteger(0);

        return Flux.fromIterable(item.getCameras())
                .flatMap(cam -> processCameraEntry(cam, now, analyzed)
                        .onErrorResume(ex -> {
                            log.warn("Failed to persist camera {}: {}", cam.getCameraId(), ex.getMessage());
                            return Mono.empty();
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("Ingested {} cameras, triggered analysis for {}",
                        item.getCameras().size(), analyzed.get()));
    }

    private Mono<Void> processCameraEntry(GovTrafficImageResponse.Camera cam,
                                          OffsetDateTime now,
                                          AtomicInteger analyzedCounter) {
        return upsertCamera(cam, now)
                .flatMap(camera -> upsertSnapshot(cam)
                        .flatMap(snapshot -> {
                            log.info(">>> processCameraEntry flatMap reached for camera {}", camera.getCameraId());
                            analyzedCounter.incrementAndGet();
                            return cameraAnalysisService.analyzeCamera(
                                    camera.getCameraId(),
                                    snapshot.getId(),
                                    cam.getImage(),
                                    camera.getLocationName()
                            );
                        })
                );
    }

    private Mono<Camera> upsertCamera(GovTrafficImageResponse.Camera cam, OffsetDateTime now) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        return cameraRepository.findByCameraId(cameraId)
                .flatMap(existing -> {
                    existing.setLatitude(BigDecimal.valueOf(cam.getLocation().getLatitude()));
                    existing.setLongitude(BigDecimal.valueOf(cam.getLocation().getLongitude()));
                    existing.setLastSeenAt(now);
                    if (cam.getImageMetadata() != null) {
                        existing.setResolution(resolveResolution(cam.getImageMetadata().getWidth()));
                    }
                    Mono<Camera> save = cameraRepository.save(existing);
                    return save;
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
                .doOnError(ex -> log.error("Failed to upsert camera {}: {}", cameraId, ex.getMessage()));
    }

    /**
     * Saves a new snapshot if the MD5 has changed. Returns the saved snapshot, or empty if unchanged.
     */
    private Mono<CameraSnapshot> upsertSnapshot(GovTrafficImageResponse.Camera cam) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        String newMd5 = cam.getImageMetadata() != null ? cam.getImageMetadata().getMd5() : "";
        OffsetDateTime ts = OffsetDateTime.parse(cam.getTimestamp());

        return snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId)
                .map(existing -> existing.getImageMd5().equals(newMd5))  // true = MD5 unchanged
                .defaultIfEmpty(false)                                    // no prior snapshot = changed
                .flatMap(md5Unchanged -> {
                    if (md5Unchanged) {
                        return Mono.empty();
                    }
                    CameraSnapshot snapshot = CameraSnapshot.builder()
                            .cameraId(cameraId)
                            .timestamp(ts)
                            .imageUrl(cam.getImage())
                            .imageMd5(newMd5)
                            .imageWidth(cam.getImageMetadata() != null ? cam.getImageMetadata().getWidth() : null)
                            .imageHeight(cam.getImageMetadata() != null ? cam.getImageMetadata().getHeight() : null)
                            .createdAt(OffsetDateTime.now())
                            .build();
                    return snapshotRepository.save(snapshot);
                });
    }

    private String resolveResolution(int width) {
        return width >= 1920 ? "HD" : "SD";
    }
}
