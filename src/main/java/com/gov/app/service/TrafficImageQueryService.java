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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageQueryService {

    private final CameraRepository cameraRepository;
    private final CameraSnapshotRepository snapshotRepository;
    private final CameraAnalysisRepository analysisRepository;
    private final ExpresswayMapping expresswayMapping;

    public CameraListAllResponse listAllCameras() {
        List<Camera> cameras = cameraRepository.findAll();
        Map<Long, CameraSnapshot> snapshots = snapshotRepository.findLatestPerCamera()
                .stream().collect(Collectors.toMap(CameraSnapshot::getCameraId, s -> s));
        Map<Long, CameraAnalysis> analyses = analysisRepository.findAll()
                .stream().collect(Collectors.toMap(CameraAnalysis::getCameraId, a -> a));
        return CameraListAllResponse.builder()
                .cameras(toCameraDetails(cameras, snapshots, analyses))
                .build();
    }

    public CameraDetail getCameraById(Long cameraId) {
        Camera camera = cameraRepository.findByCameraId(cameraId)
                .orElseThrow(() -> new NotFoundException("Camera not found: " + cameraId));
        Optional<CameraSnapshot> snapshot = snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId);
        Optional<CameraAnalysis> analysis = analysisRepository.findByCameraId(cameraId);
        return toCameraDetail(camera, snapshot.orElse(null), analysis.orElse(null));
    }

    public CameraListResponse getCamerasByExpressway(String code) {
        ExpresswayMapping.Expressway expressway = expresswayMapping.getByCode(code);
        if (expressway == null) {
            throw new NotFoundException("Unknown expressway code: " + code);
        }
        List<Long> cameraIds = expressway.cameraIds();
        List<Camera> cameras = cameraRepository.findByExpressway(code.toUpperCase());
        Map<Long, CameraSnapshot> snapshots = snapshotRepository.findLatestByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraSnapshot::getCameraId, s -> s));
        Map<Long, CameraAnalysis> analyses = analysisRepository.findByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraAnalysis::getCameraId, a -> a));
        return buildCameraList(code, expressway.name(), cameras, snapshots, analyses);
    }

    public SearchResponse searchCameras(String keyword) {
        List<Camera> cameras = cameraRepository.searchByLocationName(keyword);
        if (cameras.isEmpty()) {
            return SearchResponse.builder().query(keyword).cameras(List.of()).build();
        }
        List<Long> cameraIds = cameras.stream().map(Camera::getCameraId).toList();
        Map<Long, CameraSnapshot> snapshots = snapshotRepository.findLatestByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraSnapshot::getCameraId, s -> s));
        Map<Long, CameraAnalysis> analyses = analysisRepository.findByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraAnalysis::getCameraId, a -> a));
        return SearchResponse.builder()
                .query(keyword)
                .cameras(toCameraDetails(cameras, snapshots, analyses))
                .build();
    }

    public NearbyResponse getNearbyCameras(double lat, double lng, int radius) {
        List<Camera> cameras = cameraRepository.findAll().stream()
                .filter(camera -> haversine(lat, lng,
                        camera.getLatitude().doubleValue(), camera.getLongitude().doubleValue()) <= radius)
                .toList();
        if (cameras.isEmpty()) {
            return NearbyResponse.builder().lat(lat).lng(lng).radius(radius).cameras(List.of()).build();
        }
        List<Long> cameraIds = cameras.stream().map(Camera::getCameraId).toList();
        Map<Long, CameraSnapshot> snapshots = snapshotRepository.findLatestByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraSnapshot::getCameraId, s -> s));
        Map<Long, CameraAnalysis> analyses = analysisRepository.findByCameraIds(cameraIds)
                .stream().collect(Collectors.toMap(CameraAnalysis::getCameraId, a -> a));
        return NearbyResponse.builder()
                .lat(lat).lng(lng).radius(radius)
                .cameras(toCameraDetails(cameras, snapshots, analyses))
                .build();
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
