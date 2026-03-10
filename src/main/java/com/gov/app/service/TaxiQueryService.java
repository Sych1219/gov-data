package com.gov.app.service;

import com.gov.app.domain.TaxiSnapshot;
import com.gov.app.dto.*;
import com.gov.app.exception.NotFoundException;
import com.gov.app.exception.ValidationException;
import com.gov.app.repository.TaxiPositionRepository;
import com.gov.app.repository.TaxiSnapshotRepository;
import com.gov.app.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaxiQueryService {

    private final TaxiSnapshotRepository snapshotRepository;
    private final TaxiPositionRepository positionRepository;
    private final ZoneRepository zoneRepository;
    private final DatabaseClient db;

    // ── Snapshot resolution ────────────────────────────────────────────────────

    /**
     * Resolves the snapshot to use. If datetime is null, returns the latest snapshot.
     * Otherwise, returns the latest snapshot at or before the given datetime.
     */
    public Mono<TaxiSnapshot> resolveSnapshot(String datetime) {
        if (datetime == null) {
            return snapshotRepository.findFirstByOrderByApiTimestampDesc()
                    .switchIfEmpty(Mono.error(new NotFoundException("No taxi snapshots available")));
        }
        OffsetDateTime target;
        try {
            target = OffsetDateTime.parse(datetime);
        } catch (Exception e) {
            return Mono.error(new ValidationException("Invalid datetime format: " + datetime));
        }
        return snapshotRepository.findTopByApiTimestampLessThanEqualOrderByApiTimestampDesc(target)
                .switchIfEmpty(Mono.error(new NotFoundException("No snapshot available at or before " + datetime)));
    }

    // ── Taxis within radius (count + locations) ────────────────────────────────

    public Mono<TaxiNearbyCountResponse> nearby(double lat, double lon, int radiusM, int limit, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> Mono.zip(
                        positionRepository.countNearby(snapshot.getId(), lat, lon, radiusM),
                        toFeatureList(positionRepository.listNearby(snapshot.getId(), lat, lon, radiusM, limit)),
                        (count, features) -> TaxiNearbyCountResponse.builder()
                                .taxiCount(count)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .query(TaxiNearbyCountResponse.QueryParams.builder()
                                        .lat(lat).lon(lon).radiusM(radiusM).build())
                                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                .build()));
    }

    // ── Nearest N taxis ────────────────────────────────────────────────────────

    public Mono<TaxiNearestResponse> findNearest(double lat, double lon, int limit, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository
                        .findNearest(snapshot.getId(), lat, lon, limit)
                        .map(row -> TaxiNearestResponse.Feature.builder()
                                .geometry(TaxiNearestResponse.Geometry.builder()
                                        .coordinates(new double[]{row[0], row[1]})
                                        .build())
                                .properties(TaxiNearestResponse.Properties.builder()
                                        .distanceM(row[2])
                                        .build())
                                .build())
                        .collectList()
                        .map(features -> TaxiNearestResponse.builder()
                                .taxiCount(features.size())
                                .snapshotTime(snapshot.getApiTimestamp())
                                .query(TaxiNearestResponse.QueryParams.builder()
                                        .lat(lat).lon(lon).limit(limit).build())
                                .locations(TaxiNearestResponse.Locations.builder()
                                        .features(features)
                                        .build())
                                .build()));
    }

    // ── Zone count ─────────────────────────────────────────────────────────────

    public Mono<TaxiZoneCountResponse> countInZone(String zoneName, String datetime) {
        return zoneRepository.findBestDistrictMatch(zoneName, 0.3)
                .switchIfEmpty(
                        zoneRepository.findBestRoadMatch(zoneName, 0.3)
                                .flatMap(roadMatch -> Mono.<String>error(new NotFoundException(
                                        "'" + zoneName + "' is not a district — it matched a road/highway '" + roadMatch + "'. " +
                                        "Use GET /api/v1/taxis/road/" + roadMatch + "/count instead.")))
                                .switchIfEmpty(
                                        zoneRepository.findDistrictSuggestions(zoneName, 3)
                                                .collectList()
                                                .flatMap(suggestions -> Mono.error(new NotFoundException(
                                                        "Zone not found: '" + zoneName + "'. " +
                                                        "Did you mean: " + suggestions + "? " +
                                                        "Check GET /api/v1/zones for all available zone names.")))
                                )
                )
                .flatMap(resolvedName -> resolveSnapshot(datetime)
                        .flatMap(snapshot -> Mono.zip(
                                positionRepository.countInZone(snapshot.getId(), resolvedName),
                                toFeatureList(positionRepository.listInZone(snapshot.getId(), resolvedName)),
                                (count, features) -> TaxiZoneCountResponse.builder()
                                        .zone(resolvedName)
                                        .taxiCount(count)
                                        .snapshotTime(snapshot.getApiTimestamp())
                                        .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                        .build())));
    }

    // ── Custom polygon count ───────────────────────────────────────────────────

    public Mono<TaxiPolygonCountResponse> countInPolygon(String polygonGeoJson, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> Mono.zip(
                        positionRepository.countInPolygon(snapshot.getId(), polygonGeoJson),
                        toFeatureList(positionRepository.listInPolygon(snapshot.getId(), polygonGeoJson)),
                        (count, features) -> TaxiPolygonCountResponse.builder()
                                .taxiCount(count)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                .build()));
    }

    // ── Road count ─────────────────────────────────────────────────────────────

    public Mono<TaxiRoadCountResponse> countNearRoad(String roadName, int bufferM, String datetime) {
        return zoneRepository.findBestRoadMatch(roadName, 0.3)
                .switchIfEmpty(
                        zoneRepository.findRoadSuggestions(roadName, 3)
                                .collectList()
                                .flatMap(suggestions -> Mono.error(new NotFoundException(
                                        "Road not found: '" + roadName + "'. " +
                                        "Did you mean: " + suggestions + "? " +
                                        "Check GET /api/v1/zones?category=road for all available road names.")))
                )
                .flatMap(resolvedName -> resolveSnapshot(datetime)
                        .flatMap(snapshot -> Mono.zip(
                                positionRepository.countNearRoad(snapshot.getId(), resolvedName, bufferM),
                                toFeatureList(positionRepository.listNearRoad(snapshot.getId(), resolvedName, bufferM)),
                                (count, features) -> TaxiRoadCountResponse.builder()
                                        .road(resolvedName)
                                        .taxiCount(count)
                                        .snapshotTime(snapshot.getApiTimestamp())
                                        .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                        .build())));
    }

    // ── Route count ────────────────────────────────────────────────────────────

    public Mono<TaxiRouteCountResponse> countAlongRoute(String routeGeoJson, int bufferM, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> Mono.zip(
                        positionRepository.countAlongRoute(snapshot.getId(), routeGeoJson, bufferM),
                        toFeatureList(positionRepository.listAlongRoute(snapshot.getId(), routeGeoJson, bufferM)),
                        (count, features) -> TaxiRouteCountResponse.builder()
                                .taxiCount(count)
                                .bufferM(bufferM)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                .build()));
    }

    // ── History snapshots ──────────────────────────────────────────────────────

    public Mono<TaxiHistoryResponse> getHistory(String start, String end, String zoneName) {
        OffsetDateTime startTime;
        OffsetDateTime endTime;
        try {
            startTime = OffsetDateTime.parse(start.replace(" ", "+"));
            endTime = OffsetDateTime.parse(end.replace(" ", "+"));
        } catch (Exception e) {
            return Mono.error(new ValidationException("Invalid datetime format. Expected ISO-8601 SGT, e.g. 2026-03-09T00:00:00+08:00"));
        }

        if (!startTime.isBefore(endTime)) {
            return Mono.error(new ValidationException("'start' must be before 'end'"));
        }

        if (Duration.between(startTime, endTime).toDays() > 7) {
            return Mono.error(new ValidationException("Time range must not exceed 7 days"));
        }

        if (zoneName == null) {
            return snapshotRepository
                    .findByApiTimestampBetweenOrderByApiTimestampAsc(startTime, endTime)
                    .map(s -> TaxiHistoryResponse.SnapshotEntry.builder()
                            .apiTimestamp(s.getApiTimestamp())
                            .taxiCount(s.getTaxiCount())
                            .build())
                    .collectList()
                    .map(entries -> TaxiHistoryResponse.builder().snapshots(entries).build());
        }

        // With zone filter: resolve zone name via fuzzy match, then run the join query.
        // ST_Within automatically uses the GIST index for bounding box pre-filtering in modern PostGIS.
        String sql = """
                SELECT ts.api_timestamp, COUNT(tp.id) AS zone_taxi_count
                FROM taxi_snapshots ts
                JOIN taxi_positions tp ON tp.snapshot_id = ts.id
                JOIN zones z ON z.name = :zoneName
                WHERE ts.api_timestamp BETWEEN :start AND :end
                  AND ST_Within(tp.geog::geometry, z.geog::geometry)
                GROUP BY ts.api_timestamp
                ORDER BY ts.api_timestamp
                """;

        return zoneRepository.findBestDistrictMatch(zoneName, 0.3)
                .switchIfEmpty(
                        zoneRepository.findDistrictSuggestions(zoneName, 3)
                                .collectList()
                                .flatMap(suggestions -> Mono.error(new NotFoundException(
                                        "Zone not found: '" + zoneName + "'. " +
                                        "Did you mean: " + suggestions + "? " +
                                        "Check GET /api/v1/zones for all available zone names.")))
                )
                .flatMap(resolvedName -> db.sql(sql)
                .bind("zoneName", resolvedName)
                .bind("start", startTime)
                .bind("end", endTime)
                .map(row -> TaxiHistoryResponse.SnapshotEntry.builder()
                        .apiTimestamp(row.get("api_timestamp", OffsetDateTime.class))
                        .taxiCount(row.get("zone_taxi_count", Long.class).intValue())
                        .build())
                .all()
                .collectList()
                .map(entries -> TaxiHistoryResponse.builder().snapshots(entries).build()));
    }

    // ── Shared helpers ─────────────────────────────────────────────────────────

    private Mono<List<TaxiNearbyListResponse.Feature>> toFeatureList(Flux<double[]> coords) {
        return coords.map(coord -> TaxiNearbyListResponse.Feature.builder()
                        .geometry(TaxiNearbyListResponse.Geometry.builder()
                                .coordinates(coord)
                                .build())
                        .build())
                .collectList();
    }

    // ── Recent activity (timeline) ─────────────────────────────────────────────

    public Mono<TaxiTimelineResponse> getRecentTimeline(int minutes) {
        OffsetDateTime to = OffsetDateTime.now();
        OffsetDateTime from = to.minusMinutes(minutes);

        return snapshotRepository.findByApiTimestampBetweenOrderByApiTimestampAsc(from, to)
                .flatMapSequential(snapshot ->
                        toFeatureList(positionRepository.listForSnapshot(snapshot.getId()))
                                .map(features -> TaxiTimelineResponse.SnapshotEntry.builder()
                                        .timestamp(snapshot.getApiTimestamp())
                                        .taxiCount(snapshot.getTaxiCount())
                                        .locations(GeoJsonFeatureCollection.builder().features(features).build())
                                        .build()))
                .collectList()
                .map(entries -> TaxiTimelineResponse.builder()
                        .windowMinutes(minutes)
                        .fromTime(entries.isEmpty() ? from : entries.get(0).getTimestamp())
                        .toTime(entries.isEmpty() ? to : entries.get(entries.size() - 1).getTimestamp())
                        .snapshots(entries)
                        .build());
    }
}
