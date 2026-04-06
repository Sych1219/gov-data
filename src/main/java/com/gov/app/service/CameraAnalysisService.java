package com.gov.app.service;

import com.gov.app.dto.upstream.CivicAppAnalysisResponse;
import com.gov.app.repository.CameraAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraAnalysisService {

    private static final String ANALYZE_PATH = "/api/analyze-camera";

    private final WebClient civicAppWebClient;
    private final CameraAnalysisRepository analysisRepository;

    public Mono<Void> analyzeCamera(Long cameraId, Long snapshotId, String imageUrl, String locationName) {
        return callCivicApp(cameraId, imageUrl, locationName)
                .flatMap(response -> saveAnalysis(cameraId, snapshotId, response))
                .onErrorResume(ex -> {
                    log.warn("Analysis skipped for camera {}: {}", cameraId, ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<CivicAppAnalysisResponse> callCivicApp(Long cameraId, String imageUrl, String locationName) {
        Map<String, String> body = new HashMap<>();
        body.put("image_url", imageUrl);
        body.put("camera_id", String.valueOf(cameraId));
        if (locationName != null) {
            body.put("location_name", locationName);
        }

        return civicAppWebClient.post()
                .uri(ANALYZE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CivicAppAnalysisResponse.class);
    }

    private Mono<Void> saveAnalysis(Long cameraId, Long snapshotId, CivicAppAnalysisResponse response) {
        if (response.getAnalysis() == null) {
            log.warn("civic-app returned null analysis for camera {}, skipping UPSERT", cameraId);
            return Mono.empty();
        }
        CivicAppAnalysisResponse.Analysis a = response.getAnalysis();
        return analysisRepository.upsert(
                cameraId, snapshotId,
                a.getCongestion(), a.getVehicleDensity(),
                a.getIncidents(), a.getWeather(), a.getRoadSurface(), a.getSummary()
        );
    }
}
