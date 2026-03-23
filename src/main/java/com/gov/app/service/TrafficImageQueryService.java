package com.gov.app.service;

import com.gov.app.config.ExpresswayMapping;
import com.gov.app.domain.Camera;
import com.gov.app.domain.CameraSnapshot;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraListAllResponse;
import com.gov.app.dto.response.CameraListResponse;
import com.gov.app.dto.response.NearbyResponse;
import com.gov.app.dto.response.SearchResponse;
import com.gov.app.exception.NotFoundException;
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
    private final ExpresswayMapping expresswayMapping;

    public Mono<CameraListAllResponse> listAllCameras() {
        return cameraRepository.findAll()
                .collectList()
                .flatMap(cameras -> snapshotRepository.findLatestPerCamera()
                        .collectMap(CameraSnapshot::getCameraId)
                        .map(snapshots -> CameraListAllResponse.builder()
                                .cameras(toCameraDetails(cameras, snapshots))
                                .build()));
    }

    public Mono<CameraDetail> getCameraById(Long cameraId) {
        return cameraRepository.findById(cameraId)
                .switchIfEmpty(Mono.error(new NotFoundException("Camera not found: " + cameraId)))
                .flatMap(camera -> snapshotRepository.findTopByCameraIdOrderByTimestampDesc(cameraId)
                        .map(snapshot -> toCameraDetail(camera, snapshot))
                        .defaultIfEmpty(toCameraDetail(camera, null)));
    }

    public Mono<CameraListResponse> getCamerasByExpressway(String code) {
        ExpresswayMapping.Expressway expressway = expresswayMapping.getByCode(code);
        if (expressway == null) {
            return Mono.error(new NotFoundException("Unknown expressway code: " + code));
        }

        Long[] cameraIds = expressway.cameraIds().toArray(Long[]::new);
        return cameraRepository.findByExpressway(code.toUpperCase())
                .collectList()
                .flatMap(cameras -> snapshotRepository.findLatestByCameraIds(cameraIds)
                        .collectMap(CameraSnapshot::getCameraId)
                        .map(snapshots -> buildCameraList(code, expressway.name(), cameras, snapshots)));
    }

    public Mono<SearchResponse> searchCameras(String keyword) {
        return cameraRepository.searchByLocationName(keyword)
                .collectList()
                .flatMap(cameras -> {
                    if (cameras.isEmpty()) {
                        return Mono.just(SearchResponse.builder()
                                .query(keyword)
                                .cameras(List.of())
                                .build());
                    }
                    Long[] cameraIds = cameras.stream().map(Camera::getCameraId).toArray(Long[]::new);
                    return snapshotRepository.findLatestByCameraIds(cameraIds)
                            .collectMap(CameraSnapshot::getCameraId)
                            .map(snapshots -> SearchResponse.builder()
                                    .query(keyword)
                                    .cameras(toCameraDetails(cameras, snapshots))
                                    .build());
                });
    }

    public Mono<NearbyResponse> getNearbyCameras(double lat, double lng, int radius) {
        // For MVP, do a simple distance filter on all cameras
        return cameraRepository.findAll()
                .filter(camera -> {
                    double dist = haversine(lat, lng,
                            camera.getLatitude().doubleValue(), camera.getLongitude().doubleValue());
                    return dist <= radius;
                })
                .collectList()
                .flatMap(cameras -> {
                    if (cameras.isEmpty()) {
                        return Mono.just(NearbyResponse.builder()
                                .lat(lat).lng(lng).radius(radius)
                                .cameras(List.of())
                                .build());
                    }
                    Long[] cameraIds = cameras.stream().map(Camera::getCameraId).toArray(Long[]::new);
                    return snapshotRepository.findLatestByCameraIds(cameraIds)
                            .collectMap(CameraSnapshot::getCameraId)
                            .map(snapshots -> NearbyResponse.builder()
                                    .lat(lat).lng(lng).radius(radius)
                                    .cameras(toCameraDetails(cameras, snapshots))
                                    .build());
                });
    }

    private CameraListResponse buildCameraList(String code, String name,
                                                List<Camera> cameras,
                                                Map<Long, CameraSnapshot> snapshots) {
        List<CameraDetail> details = cameras.stream()
                .map(cam -> toCameraDetail(cam, snapshots.get(cam.getCameraId())))
                .toList();

        long online = snapshots.size();

        return CameraListResponse.builder()
                .expressway(code)
                .name(name)
                .camerasOnline((int) online)
                .camerasTotal(cameras.size())
                .cameras(details)
                .build();
    }

    private List<CameraDetail> toCameraDetails(List<Camera> cameras, Map<Long, CameraSnapshot> snapshots) {
        return cameras.stream()
                .map(cam -> toCameraDetail(cam, snapshots.get(cam.getCameraId())))
                .toList();
    }

    private CameraDetail toCameraDetail(Camera camera, CameraSnapshot snapshot) {
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

        return builder.build();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
