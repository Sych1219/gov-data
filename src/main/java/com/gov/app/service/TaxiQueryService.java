package com.gov.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gov.app.domain.TaxiSnapshot;
import com.gov.app.dto.GeoJsonFeature;
import com.gov.app.dto.GeoJsonFeatureCollection;
import com.gov.app.dto.GeoJsonPolygon;
import com.gov.app.dto.context.*;
import com.gov.app.dto.response.SpatialQueryData;
import com.gov.app.dto.response.TimelineData;
import com.gov.app.exception.NotFoundException;
import com.gov.app.exception.ValidationException;
import com.gov.app.repository.TaxiPositionRepository;
import com.gov.app.repository.TaxiSnapshotRepository;
import com.gov.app.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaxiQueryService {

    private final TaxiSnapshotRepository snapshotRepository;
    private final TaxiPositionRepository positionRepository;
    private final ZoneRepository zoneRepository;
    private final ObjectMapper objectMapper;

    // ── Snapshot resolution ────────────────────────────────────────────────────

    public TaxiSnapshot resolveSnapshot(String datetime) {
        if (datetime == null) {
            return snapshotRepository.findFirstByOrderByApiTimestampDesc()
                    .orElseThrow(() -> new NotFoundException("No taxi snapshots available"));
        }
        OffsetDateTime target;
        try {
            target = OffsetDateTime.parse(datetime);
        } catch (Exception e) {
            throw new ValidationException("Invalid datetime format: " + datetime);
        }
        return snapshotRepository.findTopByApiTimestampLessThanEqualOrderByApiTimestampDesc(target)
                .orElseThrow(() -> new NotFoundException("No snapshot available at or before " + datetime));
    }

    // ── Taxis within radius ────────────────────────────────────────────────────

    public SpatialQueryData nearby(double lat, double lon, int radiusM, int limit, String datetime) {
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        long count = positionRepository.countNearby(snapshot.getId(), lat, lon, radiusM);
        List<GeoJsonFeature> features = toFeatureList(
                positionRepository.listNearby(snapshot.getId(), lat, lon, radiusM, limit));
        return SpatialQueryData.builder()
                .taxiCount((int) count)
                .snapshotTime(snapshot.getApiTimestamp())
                .context(RadiusContext.builder().lat(lat).lon(lon).radiusM(radiusM).build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── Nearest N taxis ────────────────────────────────────────────────────────

    public SpatialQueryData findNearest(double lat, double lon, int limit, String datetime) {
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        List<GeoJsonFeature> features = positionRepository.findNearest(snapshot.getId(), lat, lon, limit)
                .stream()
                .map(row -> GeoJsonFeature.builder()
                        .geometry(GeoJsonFeature.Geometry.builder()
                                .coordinates(new double[]{row[0], row[1]}).build())
                        .properties(Map.of("distance_m", row[2]))
                        .build())
                .toList();
        return SpatialQueryData.builder()
                .taxiCount(features.size())
                .snapshotTime(snapshot.getApiTimestamp())
                .context(NearestContext.builder().lat(lat).lon(lon).limit(limit).build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── Zone count ─────────────────────────────────────────────────────────────

    public SpatialQueryData countInZone(String zoneName, String datetime) {
        Optional<String> districtMatch = zoneRepository.findBestDistrictMatch(zoneName, 0.3);
        if (districtMatch.isEmpty()) {
            Optional<String> roadMatch = zoneRepository.findBestRoadMatch(zoneName, 0.3);
            if (roadMatch.isPresent()) {
                throw new NotFoundException("'" + zoneName + "' is not a district — it matched a road/highway '"
                        + roadMatch.get() + "'. Use GET /api/v1/taxis/road/count?roadName=" + roadMatch.get() + " instead.");
            }
            List<String> suggestions = zoneRepository.findDistrictSuggestions(zoneName, 3);
            throw new NotFoundException("Zone not found: '" + zoneName + "'. Did you mean: " + suggestions
                    + "? Check GET /api/v1/zones for all available zone names.");
        }
        String resolvedName = districtMatch.get();
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        long count = positionRepository.countInZone(snapshot.getId(), resolvedName);
        List<GeoJsonFeature> features = toFeatureList(positionRepository.listInZone(snapshot.getId(), resolvedName));
        return SpatialQueryData.builder()
                .taxiCount((int) count)
                .snapshotTime(snapshot.getApiTimestamp())
                .context(ZoneContext.builder().zoneName(resolvedName).category("district").build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── Custom polygon count ───────────────────────────────────────────────────

    public SpatialQueryData countInPolygon(GeoJsonPolygon polygon, String datetime) {
        String polygonGeoJson;
        try {
            polygonGeoJson = objectMapper.writeValueAsString(polygon);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid polygon GeoJSON");
        }
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        long count = positionRepository.countInPolygon(snapshot.getId(), polygonGeoJson);
        List<GeoJsonFeature> features = toFeatureList(positionRepository.listInPolygon(snapshot.getId(), polygonGeoJson));
        return SpatialQueryData.builder()
                .taxiCount((int) count)
                .snapshotTime(snapshot.getApiTimestamp())
                .context(PolygonContext.builder().polygon(polygon).build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── Road count ─────────────────────────────────────────────────────────────

    public SpatialQueryData countNearRoad(String roadName, int bufferM, String datetime) {
        Optional<com.gov.app.domain.Zone> zoneOpt = zoneRepository.findBestRoadZone(roadName, 0.3);
        if (zoneOpt.isEmpty()) {
            List<String> suggestions = zoneRepository.findRoadSuggestions(roadName, 3);
            throw new NotFoundException("Road not found: '" + roadName + "'. Did you mean: " + suggestions
                    + "? Check GET /api/v1/zones?category=road for all available road names.");
        }
        com.gov.app.domain.Zone zone = zoneOpt.get();
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        long count = positionRepository.countNearRoad(snapshot.getId(), zone.getName(), bufferM);
        List<GeoJsonFeature> features = toFeatureList(positionRepository.listNearRoad(snapshot.getId(), zone.getName(), bufferM));
        return SpatialQueryData.builder()
                .taxiCount((int) count)
                .snapshotTime(snapshot.getApiTimestamp())
                .context(RoadContext.builder()
                        .roadName(zone.getName())
                        .category(zone.getCategory())
                        .bufferM(bufferM)
                        .build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── Route count ────────────────────────────────────────────────────────────

    public SpatialQueryData countAlongRoute(JsonNode route, int bufferM, String datetime) {
        String routeGeoJson;
        try {
            routeGeoJson = objectMapper.writeValueAsString(route);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid route GeoJSON");
        }
        TaxiSnapshot snapshot = resolveSnapshot(datetime);
        long count = positionRepository.countAlongRoute(snapshot.getId(), routeGeoJson, bufferM);
        List<GeoJsonFeature> features = toFeatureList(positionRepository.listAlongRoute(snapshot.getId(), routeGeoJson, bufferM));
        return SpatialQueryData.builder()
                .taxiCount((int) count)
                .snapshotTime(snapshot.getApiTimestamp())
                .context(RouteContext.builder().route(route).bufferM(bufferM).build())
                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                .build();
    }

    // ── History snapshots ──────────────────────────────────────────────────────

    public TimelineData getHistory(String start, String end, String zoneName) {
        OffsetDateTime startTime;
        OffsetDateTime endTime;
        try {
            startTime = OffsetDateTime.parse(start.replace(" ", "+"));
            endTime = OffsetDateTime.parse(end.replace(" ", "+"));
        } catch (Exception e) {
            throw new ValidationException("Invalid datetime format. Expected ISO-8601 SGT, e.g. 2026-03-09T00:00:00+08:00");
        }
        if (!startTime.isBefore(endTime)) {
            throw new ValidationException("'start' must be before 'end'");
        }
        if (Duration.between(startTime, endTime).toDays() > 7) {
            throw new ValidationException("Time range must not exceed 7 days");
        }
        if (zoneName == null) {
            return buildTimeline(startTime, endTime, null, null);
        }
        Optional<String> match = zoneRepository.findBestDistrictMatch(zoneName, 0.3);
        if (match.isEmpty()) {
            List<String> suggestions = zoneRepository.findDistrictSuggestions(zoneName, 3);
            throw new NotFoundException("Zone not found: '" + zoneName + "'. Did you mean: " + suggestions
                    + "? Check GET /api/v1/zones for all available zone names.");
        }
        String resolvedName = match.get();
        return buildTimeline(startTime, endTime, resolvedName,
                ZoneContext.builder().zoneName(resolvedName).category("district").build());
    }

    private TimelineData buildTimeline(OffsetDateTime from, OffsetDateTime to, String zoneName, QueryContext context) {
        List<TaxiSnapshot> snapshots = snapshotRepository.findByApiTimestampBetweenOrderByApiTimestampAsc(from, to);
        List<TimelineData.SnapshotEntry> entries = new ArrayList<>();
        for (TaxiSnapshot snapshot : snapshots) {
            long count = zoneName == null
                    ? snapshot.getTaxiCount()
                    : positionRepository.countInZone(snapshot.getId(), zoneName);
            entries.add(TimelineData.SnapshotEntry.builder()
                    .snapshotId(snapshot.getId())
                    .timestamp(snapshot.getApiTimestamp())
                    .taxiCount((int) count)
                    .build());
        }
        return TimelineData.builder()
                .fromTime(entries.isEmpty() ? from : entries.get(0).getTimestamp())
                .toTime(entries.isEmpty() ? to : entries.get(entries.size() - 1).getTimestamp())
                .context(context)
                .snapshots(entries)
                .build();
    }

    // ── Recent activity ────────────────────────────────────────────────────────

    public TimelineData getRecentTimeline(int minutes) {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusMinutes(minutes);
        List<TaxiSnapshot> snapshots = snapshotRepository.findByApiTimestampBetweenOrderByApiTimestampAsc(from, to);
        List<TimelineData.SnapshotEntry> entries = new ArrayList<>();
        for (TaxiSnapshot snapshot : snapshots) {
            List<GeoJsonFeature> features = toFeatureList(positionRepository.listForSnapshot(snapshot.getId()));
            entries.add(TimelineData.SnapshotEntry.builder()
                    .timestamp(snapshot.getApiTimestamp())
                    .taxiCount(snapshot.getTaxiCount())
                    .locations(GeoJsonFeatureCollection.builder().features(features).build())
                    .build());
        }
        return TimelineData.builder()
                .windowMinutes(minutes)
                .fromTime(entries.isEmpty() ? from : entries.get(0).getTimestamp())
                .toTime(entries.isEmpty() ? to : entries.get(entries.size() - 1).getTimestamp())
                .snapshots(entries)
                .build();
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private List<GeoJsonFeature> toFeatureList(List<double[]> coords) {
        return coords.stream()
                .map(coord -> GeoJsonFeature.builder()
                        .geometry(GeoJsonFeature.Geometry.builder().coordinates(coord).build())
                        .properties(null)
                        .build())
                .toList();
    }
}
