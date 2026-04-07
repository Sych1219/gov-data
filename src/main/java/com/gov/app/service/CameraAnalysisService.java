package com.gov.app.service;

import com.gov.app.dto.upstream.CivicAppAnalysisResponse;
import com.gov.app.repository.CameraAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraAnalysisService {

    private static final String ANALYZE_PATH = "/api/analyze-camera";

    private final RestClient civicAppWebClient;
    private final CameraAnalysisRepository analysisRepository;

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
