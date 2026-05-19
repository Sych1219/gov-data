package com.gov.app.service;

import com.gov.app.config.ExpresswayMapping;
import com.gov.app.dto.response.CameraAnalysisDetail;
import com.gov.app.dto.response.CameraDetail;
import com.gov.app.dto.response.CameraMeta;
import com.gov.app.dto.response.CameraQueryResponse;
import com.gov.app.exception.NotFoundException;
import com.gov.app.repository.CameraDetailProjection;
import com.gov.app.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficImageQueryService {

    private final CameraRepository cameraRepository;
    private final ExpresswayMapping expresswayMapping;

    public CameraQueryResponse listAllCameras() {
        List<CameraDetail> cameras = toDetails(cameraRepository.findAllWithDetails());
        return CameraQueryResponse.builder()
                .cameras(cameras)
                .meta(CameraMeta.builder().count(cameras.size()).build())
                .build();
    }

    public CameraDetail getCameraById(Long cameraId) {
        return cameraRepository.findByIdWithDetails(cameraId)
                .map(this::toDetail)
                .orElseThrow(() -> new NotFoundException("Camera not found: " + cameraId));
    }

    public CameraQueryResponse getCamerasByExpressway(String code) {
        if (expresswayMapping.getByCode(code) == null) {
            throw new NotFoundException("Unknown expressway code: " + code);
        }
        List<CameraDetail> cameras = toDetails(
                cameraRepository.findByExpresswayWithDetails(code.toUpperCase()));
        return CameraQueryResponse.builder()
                .cameras(cameras)
                .meta(CameraMeta.builder().expressway(code.toUpperCase()).count(cameras.size()).build())
                .build();
    }

    public CameraQueryResponse searchCameras(String keyword) {
        List<CameraDetail> cameras = toDetails(
                cameraRepository.searchByLocationNameWithDetails(keyword));
        return CameraQueryResponse.builder()
                .cameras(cameras)
                .meta(CameraMeta.builder().query(keyword).count(cameras.size()).build())
                .build();
    }

    public CameraQueryResponse getNearbyCameras(double lat, double lng, int radius) {
        List<CameraDetail> cameras = toDetails(
                cameraRepository.findNearbyWithDetails(lat, lng, radius));
        return CameraQueryResponse.builder()
                .cameras(cameras)
                .meta(CameraMeta.builder().lat(lat).lng(lng).radius(radius).count(cameras.size()).build())
                .build();
    }

    private List<CameraDetail> toDetails(List<CameraDetailProjection> rows) {
        return rows.stream().map(this::toDetail).toList();
    }

    private CameraDetail toDetail(CameraDetailProjection p) {
        CameraDetail.CameraDetailBuilder builder = CameraDetail.builder()
                .cameraId(p.getCameraId())
                .locationName(p.getLocationName())
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .resolution(p.getResolution());

        if (p.getImageUrl() != null) {
            builder.latestImage(p.getImageUrl())
                    .timestamp(p.getSnapshotTimestamp() != null
                            ? p.getSnapshotTimestamp().toString() : null);
        }

        if (p.getCongestion() != null) {
            builder.analysis(CameraAnalysisDetail.builder()
                    .congestion(p.getCongestion())
                    .vehicleDensity(p.getVehicleDensity())
                    .incidents(p.getIncidents())
                    .weather(p.getWeather())
                    .roadSurface(p.getRoadSurface())
                    .summary(p.getSummary())
                    .analyzedAt(p.getAnalyzedAt() != null ? p.getAnalyzedAt().toString() : null)
                    .build());
        }

        return builder.build();
    }
}
