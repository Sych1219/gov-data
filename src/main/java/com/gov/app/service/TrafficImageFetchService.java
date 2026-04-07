package com.gov.app.service;

import com.gov.app.config.ExpresswayMapping;
import com.gov.app.domain.Camera;
import com.gov.app.domain.CameraSnapshot;
import com.gov.app.dto.upstream.GovTrafficImageResponse;
import com.gov.app.repository.CameraRepository;
import com.gov.app.repository.CameraSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageFetchService {

    private static final String TRAFFIC_IMAGES_PATH = "/v1/transport/traffic-images";
    private static final int MAX_RETRIES = 3;

    private final RestClient trafficImageWebClient;
    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;
    private final ExpresswayMapping expresswayMapping;
    private final CameraAnalysisService cameraAnalysisService;

    public void fetchAndSave() {
        GovTrafficImageResponse response = fetchWithRetry();
        if (response == null) {
            return;
        }
        persist(response);
    }

    private GovTrafficImageResponse fetchWithRetry() {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return trafficImageWebClient.get()
                        .uri(TRAFFIC_IMAGES_PATH)
                        .retrieve()
                        .body(GovTrafficImageResponse.class);
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
        log.error("Failed to fetch traffic images after {} retries: {}",
                MAX_RETRIES, lastException != null ? lastException.getMessage() : "unknown");
        return null;
    }

    private void persist(GovTrafficImageResponse response) {
        if (response.getItems() == null || response.getItems().isEmpty()) {
            log.warn("Traffic image API returned empty items — skipping");
            return;
        }
        GovTrafficImageResponse.Item item = response.getItems().get(0);
        if (item.getCameras() == null || item.getCameras().isEmpty()) {
            log.warn("Traffic image API returned empty cameras — skipping");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        AtomicInteger analyzed = new AtomicInteger(0);

        for (GovTrafficImageResponse.Camera cam : item.getCameras()) {
            try {
                processCameraEntry(cam, now, analyzed);
            } catch (Exception ex) {
                log.warn("Failed to persist camera {}: {}", cam.getCameraId(), ex.getMessage());
            }
        }
        log.info("Ingested {} cameras, triggered analysis for {}",
                item.getCameras().size(), analyzed.get());
    }

    private void processCameraEntry(GovTrafficImageResponse.Camera cam,
                                    OffsetDateTime now,
                                    AtomicInteger analyzedCounter) {
        Camera camera = upsertCamera(cam, now);
        CameraSnapshot snapshot = upsertSnapshot(cam);
        if (snapshot != null) {
            log.info(">>> processCameraEntry flatMap reached for camera {}", camera.getCameraId());
            analyzedCounter.incrementAndGet();
            cameraAnalysisService.analyzeCamera(
                    camera.getCameraId(),
                    snapshot.getId(),
                    cam.getImage(),
                    camera.getLocationName()
            );
        }
    }

    private Camera upsertCamera(GovTrafficImageResponse.Camera cam, OffsetDateTime now) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        Optional<Camera> existing = cameraRepository.findByCameraId(cameraId);
        if (existing.isPresent()) {
            Camera camera = existing.get();
            camera.setLatitude(BigDecimal.valueOf(cam.getLocation().getLatitude()));
            camera.setLongitude(BigDecimal.valueOf(cam.getLocation().getLongitude()));
            camera.setLastSeenAt(now);
            if (cam.getImageMetadata() != null) {
                camera.setResolution(resolveResolution(cam.getImageMetadata().getWidth()));
            }
            return cameraRepository.save(camera);
        } else {
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
        }
    }

    /**
     * Saves a new snapshot if the MD5 has changed. Returns the saved snapshot, or null if unchanged.
     */
    private CameraSnapshot upsertSnapshot(GovTrafficImageResponse.Camera cam) {
        Long cameraId = Long.parseLong(cam.getCameraId());
        String newMd5 = cam.getImageMetadata() != null ? cam.getImageMetadata().getMd5() : "";
        OffsetDateTime ts = OffsetDateTime.parse(cam.getTimestamp());

        Optional<CameraSnapshot> latest = snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId);
        if (latest.isPresent() && latest.get().getImageMd5().equals(newMd5)) {
            return null; // MD5 unchanged, skip analysis
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
    }

    private String resolveResolution(int width) {
        return width >= 1920 ? "HD" : "SD";
    }
}
