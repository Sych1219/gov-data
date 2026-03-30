package com.gov.app.service;

import com.gov.app.config.ExpresswayMapping;
import com.gov.app.domain.Camera;
import com.gov.app.domain.CameraAnalysis;
import com.gov.app.domain.CameraSnapshot;
import com.gov.app.dto.response.CameraAnalysisDetail;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraListAllResponse;
import com.gov.app.dto.response.CameraListResponse;
import com.gov.app.dto.response.NearbyResponse;
import com.gov.app.dto.response.SearchResponse;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.CameraAnalysisRepository;
import com.gov.app.repository.CameraRepository;
import com.gov.app.repository.CameraSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageQueryService {

    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;
    private final CameraAnalysisRepository analysisRepository;
    private final ExpresswayMapping expresswayMapping;

    public Mono<CameraListAllResponse> listAllCameras() {
        return cameraRepository.findAll().collectList()
                .flatMap(cameras -> {
                    Mono<Map<Long, CameraSnapshot>> snapshotsMono =
                            snapshotRepository.findLatestPerCamera().collectMap(CameraSnapshot::getCameraId);
                    Mono<Map<Long, CameraAnalysis>> analysisMono =
                            analysisRepository.findAll().collectMap(CameraAnalysis::getCameraId);
                    return Mono.zip(snapshotsMono, analysisMono)
                            .map(t -> CameraListAllResponse.builder()
                                    .cameras(toCameraDetails(cameras, t.getT1(), t.getT2()))
                                    .build());
                });
    }

    public Mono<CameraDetail> getCameraById(Long cameraId) {
        return cameraRepository.findByCameraId(cameraId)
                .switchIfEmpty(Mono.error(new NotFoundException("Camera not found: " + cameraId)))
                .flatMap(camera -> {
                    Mono<CameraSnapshot> snapshotMono =
                            snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId)
                                    .defaultIfEmpty(nullSnapshot());
                    Mono<CameraAnalysis> analysisMono =
                            analysisRepository.findByCameraId(cameraId)
                                    .defaultIfEmpty(nullAnalysis());
                    return Mono.zip(snapshotMono, analysisMono)
                            .map(t -> toCameraDetail(camera,
                                    isNull(t.getT1()) ? null : t.getT1(),
                                    isNull(t.getT2()) ? null : t.getT2()));
                });
    }

    public Mono<CameraListResponse> getCamerasByExpressway(String code) {
        ExpresswayMapping.Expressway expressway = expresswayMapping.getByCode(code);
        if (expressway == null) {
            return Mono.error(new NotFoundException("Unknown expressway code: " + code));
        }

        Long[] cameraIds = expressway.cameraIds().toArray(Long[]::new);
        return cameraRepository.findByExpressway(code.toUpperCase()).collectList()
                .flatMap(cameras -> {
                    Mono<Map<Long, CameraSnapshot>> snapshotsMono =
                            snapshotRepository.findLatestByCameraIds(cameraIds)
                                    .collectMap(CameraSnapshot::getCameraId);
                    Mono<Map<Long, CameraAnalysis>> analysisMono =
                            analysisRepository.findByCameraIds(cameraIds)
                                    .collectMap(CameraAnalysis::getCameraId);
                    return Mono.zip(snapshotsMono, analysisMono)
                            .map(t -> buildCameraList(code, expressway.name(), cameras, t.getT1(), t.getT2()));
                });
    }

    public Mono<SearchResponse> searchCameras(String keyword) {
        return cameraRepository.searchByLocationName(keyword).collectList()
                .flatMap(cameras -> {
                    if (cameras.isEmpty()) {
                        return Mono.just(SearchResponse.builder().query(keyword).cameras(List.of()).build());
                    }
                    Long[] cameraIds = cameras.stream().map(Camera::getCameraId).toArray(Long[]::new);
                    Mono<Map<Long, CameraSnapshot>> snapshotsMono =
                            snapshotRepository.findLatestByCameraIds(cameraIds)
                                    .collectMap(CameraSnapshot::getCameraId);
                    Mono<Map<Long, CameraAnalysis>> analysisMono =
                            analysisRepository.findByCameraIds(cameraIds)
                                    .collectMap(CameraAnalysis::getCameraId);
                    return Mono.zip(snapshotsMono, analysisMono)
                            .map(t -> SearchResponse.builder()
                                    .query(keyword)
                                    .cameras(toCameraDetails(cameras, t.getT1(), t.getT2()))
                                    .build());
                });
    }

    public Mono<NearbyResponse> getNearbyCameras(double lat, double lng, int radius) {
        return cameraRepository.findAll()
                .filter(camera -> haversine(lat, lng,
                        camera.getLatitude().doubleValue(), camera.getLongitude().doubleValue()) <= radius)
                .collectList()
                .flatMap(cameras -> {
                    if (cameras.isEmpty()) {
                        return Mono.just(NearbyResponse.builder()
                                .lat(lat).lng(lng).radius(radius).cameras(List.of()).build());
                    }
                    Long[] cameraIds = cameras.stream().map(Camera::getCameraId).toArray(Long[]::new);
                    Mono<Map<Long, CameraSnapshot>> snapshotsMono =
                            snapshotRepository.findLatestByCameraIds(cameraIds)
                                    .collectMap(CameraSnapshot::getCameraId);
                    Mono<Map<Long, CameraAnalysis>> analysisMono =
                            analysisRepository.findByCameraIds(cameraIds)
                                    .collectMap(CameraAnalysis::getCameraId);
                    return Mono.zip(snapshotsMono, analysisMono)
                            .map(t -> NearbyResponse.builder()
                                    .lat(lat).lng(lng).radius(radius)
                                    .cameras(toCameraDetails(cameras, t.getT1(), t.getT2()))
                                    .build());
                });
    }

    private CameraListResponse buildCameraList(String code, String name,
                                                List<Camera> cameras,
                                                Map<Long, CameraSnapshot> snapshots,
                                                Map<Long, CameraAnalysis> analyses) {
        List<CameraDetail> details = cameras.stream()
                .map(cam -> toCameraDetail(cam, snapshots.get(cam.getCameraId()), analyses.get(cam.getCameraId())))
                .toList();
        return CameraListResponse.builder()
                .expressway(code)
                .name(name)
                .camerasOnline(snapshots.size())
                .camerasTotal(cameras.size())
                .cameras(details)
                .build();
    }

    private List<CameraDetail> toCameraDetails(List<Camera> cameras,
                                                Map<Long, CameraSnapshot> snapshots,
                                                Map<Long, CameraAnalysis> analyses) {
        return cameras.stream()
                .map(cam -> toCameraDetail(cam, snapshots.get(cam.getCameraId()), analyses.get(cam.getCameraId())))
                .toList();
    }

    private CameraDetail toCameraDetail(Camera camera, CameraSnapshot snapshot, CameraAnalysis analysis) {
        CameraDetail.CameraDetailBuilder builder = CameraDetail.builder()
                .cameraId(camera.getCameraId())
                .locationName(camera.getLocationName())
                .latitude(camera.getLatitude().doubleValue())
                .longitude(camera.getLongitude().doubleValue())
                .resolution(camera.getResolution());

        if (snapshot != null) {
            builder.latestImage(snapshot.getImageUrl())
                    .timestamp(snapshot.getTimestamp().toString());
        }

        if (analysis != null) {
            builder.analysis(CameraAnalysisDetail.builder()
                    .congestion(analysis.getCongestion())
                    .vehicleDensity(analysis.getVehicleDensity())
                    .incidents(analysis.getIncidents())
                    .weather(analysis.getWeather())
                    .roadSurface(analysis.getRoadSurface())
                    .summary(analysis.getSummary())
                    .analyzedAt(analysis.getAnalyzedAt().toString())
                    .build());
        }

        return builder.build();
    }

    // Sentinel objects used with defaultIfEmpty to avoid null in Mono.zip
    private static final Long NULL_SENTINEL_ID = -1L;

    private CameraSnapshot nullSnapshot() {
        return CameraSnapshot.builder().id(NULL_SENTINEL_ID).build();
    }

    private CameraAnalysis nullAnalysis() {
        return CameraAnalysis.builder().id(NULL_SENTINEL_ID).build();
    }

    private boolean isNull(Object obj) {
        if (obj instanceof CameraSnapshot s) return NULL_SENTINEL_ID.equals(s.getId());
        if (obj instanceof CameraAnalysis a) return NULL_SENTINEL_ID.equals(a.getId());
        return obj == null;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
