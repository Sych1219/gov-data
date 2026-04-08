package com.gov.app.service;

import com.gov.app.domain.CameraSnapshot;
import com.gov.app.dto.request.StoreAnalysisRequest;
import com.gov.app.dto.response.StoreAnalysisResponse;
import com.gov.app.dto.upstream.CivicAppAnalysisResponse;
import com.gov.app.exception.ConflictException;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.CameraAnalysisRepository;
import com.gov.app.repository.CameraRepository;
import com.gov.app.repository.CameraSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraAnalysisService {

    private static final String ANALYZE_PATH = "/api/analyze-camera";

    private final RestClient civicAppWebClient;
    private final CameraAnalysisRepository analysisRepository;
    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;

    public StoreAnalysisResponse storeAnalysis(Long cameraId, StoreAnalysisRequest req) {
        cameraRepository.findByCameraId(cameraId)
                .orElseThrow(() -> new NotFoundException("Camera not found: " + cameraId));

        CameraSnapshot snapshot = snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId)
                .orElseThrow(() -> new ConflictException(
                        "No snapshot exists yet for camera " + cameraId + " — cannot store analysis"));

        analysisRepository.upsert(
                cameraId, snapshot.getId(),
                req.getCongestion(), req.getVehicleDensity(),
                req.getIncidents(), req.getWeather(), req.getRoadSurface(), req.getSummary()
        );

        String analyzedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        log.info("Stored analysis for camera {} (snapshot {})", cameraId, snapshot.getId());
        return StoreAnalysisResponse.builder()
                .cameraId(cameraId)
                .analyzedAt(analyzedAt)
                .build();
    }

    public void analyzeCamera(Long cameraId, Long snapshotId, String imageUrl, String locationName) {
        try {
            CivicAppAnalysisResponse response = callCivicApp(cameraId, imageUrl, locationName);
            saveAnalysis(cameraId, snapshotId, response);
        } catch (Exception ex) {
            log.warn("Analysis skipped for camera {}: {}", cameraId, ex.getMessage());
        }
    }

    private CivicAppAnalysisResponse callCivicApp(Long cameraId, String imageUrl, String locationName) {
        Map<String, String> body = new HashMap<>();
        body.put("image_url", imageUrl);
        body.put("camera_id", String.valueOf(cameraId));
        if (locationName != null) {
            body.put("location_name", locationName);
        }
        return civicAppWebClient.post()
                .uri(ANALYZE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(CivicAppAnalysisResponse.class);
    }

    private void saveAnalysis(Long cameraId, Long snapshotId, CivicAppAnalysisResponse response) {
        if (response == null || response.getAnalysis() == null) {
            log.warn("civic-app returned null analysis for camera {}, skipping UPSERT", cameraId);
            return;
        }
        CivicAppAnalysisResponse.Analysis a = response.getAnalysis();
        analysisRepository.upsert(
                cameraId, snapshotId,
                a.getCongestion(), a.getVehicleDensity(),
                a.getIncidents(), a.getWeather(), a.getRoadSurface(), a.getSummary()
        );
    }
}
