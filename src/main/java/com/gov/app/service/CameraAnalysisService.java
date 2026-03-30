package com.gov.app.service;

import com.gov.app.dto.upstream.CivicAppAnalysisResponse;
import com.gov.app.repository.CameraAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraAnalysisService {

    private static final String ANALYZE_PATH = "/api/analyze-camera";

    private final WebClient civicAppWebClient;
    private final WebClient imageDownloadWebClient;
    private final CameraAnalysisRepository analysisRepository;

    public Mono<Void> analyzeCamera(Long cameraId, Long snapshotId, String imageUrl, String locationName) {
        return downloadImage(imageUrl)
                .flatMap(imageBytes -> callCivicApp(cameraId, locationName, imageBytes))
                .flatMap(response -> saveAnalysis(cameraId, snapshotId, response))
                .onErrorResume(ex -> {
                    log.warn("Analysis skipped for camera {}: {}", cameraId, ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<byte[]> downloadImage(String imageUrl) {
        return imageDownloadWebClient.get()
                .uri(imageUrl)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    private Mono<CivicAppAnalysisResponse> callCivicApp(Long cameraId, String locationName, byte[] imageBytes) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("image", imageBytes).filename("camera.jpg").contentType(MediaType.IMAGE_JPEG);
        bodyBuilder.part("camera_id", String.valueOf(cameraId));
        if (locationName != null) {
            bodyBuilder.part("location_name", locationName);
        }

        return civicAppWebClient.post()
                .uri(ANALYZE_PATH)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(CivicAppAnalysisResponse.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(4)));
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
