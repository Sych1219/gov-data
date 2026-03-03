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
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

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

    // ── Count taxis within radius ──────────────────────────────────────────────

    public Mono<TaxiNearbyCountResponse> countNearby(double lat, double lon, int radiusM, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository.countNearby(snapshot.getId(), lat, lon, radiusM)
                        .map(count -> TaxiNearbyCountResponse.builder()
                                .taxiCount(count)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .query(TaxiNearbyCountResponse.QueryParams.builder()
                                        .lat(lat).lon(lon).radiusM(radiusM).build())
                                .build()));
    }

    // ── List taxis within radius ───────────────────────────────────────────────

    public Mono<TaxiNearbyListResponse> listNearby(double lat, double lon, int radiusM, int limit, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository
                        .listNearby(snapshot.getId(), lat, lon, radiusM, limit)
                        .map(coord -> TaxiNearbyListResponse.Feature.builder()
                                .geometry(TaxiNearbyListResponse.Geometry.builder()
                                        .coordinates(coord)
                                        .build())
                                .build())
                        .collectList()
                        .map(features -> TaxiNearbyListResponse.builder()
                                .features(features)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .build()));
    }

    // ── Nearest N taxis ────────────────────────────────────────────────────────

    public Mono<TaxiNearestResponse> findNearest(double lat, double lon, int limit, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository
                        .findNearest(snapshot.getId(), lat, lon, limit)
                        .map(row -> TaxiNearestResponse.TaxiPoint.builder()
                                .longitude(row[0])
                                .latitude(row[1])
                                .distanceM(row[2])
                                .build())
                        .collectList()
                        .map(taxis -> TaxiNearestResponse.builder()
                                .taxis(taxis)
                                .snapshotTime(snapshot.getApiTimestamp())
                                .build()));
    }

    // ── Zone count ─────────────────────────────────────────────────────────────

    public Mono<TaxiZoneCountResponse> countInZone(String zoneName, String datetime) {
        return zoneRepository.findBestMatch(zoneName, 0.3)
                .switchIfEmpty(
                        zoneRepository.findSuggestions(zoneName, 3)
                                .collectList()
                                .flatMap(suggestions -> Mono.error(new NotFoundException(
                                        "Zone not found: '" + zoneName + "'. " +
                                        "Did you mean: " + suggestions + "? " +
                                        "Check GET /api/v1/zones for all available zone names.")))
                )
                .flatMap(resolvedName -> resolveSnapshot(datetime)
                        .flatMap(snapshot -> positionRepository.countInZone(snapshot.getId(), resolvedName)
                                .map(count -> TaxiZoneCountResponse.builder()
                                        .zone(resolvedName)
                                        .taxiCount(count)
                                        .snapshotTime(snapshot.getApiTimestamp())
                                        .build())));
    }

    // ── Custom polygon count ───────────────────────────────────────────────────

    public Mono<Long> countInPolygon(String polygonGeoJson, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository.countInPolygon(snapshot.getId(), polygonGeoJson));
    }

    // ── Road count ─────────────────────────────────────────────────────────────

    public Mono<Long> countNearRoad(String roadName, int bufferM, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository.countNearRoad(snapshot.getId(), roadName, bufferM));
    }

    // ── Route count ────────────────────────────────────────────────────────────

    public Mono<Long> countAlongRoute(String routeGeoJson, int bufferM, String datetime) {
        return resolveSnapshot(datetime)
                .flatMap(snapshot -> positionRepository.countAlongRoute(snapshot.getId(), routeGeoJson, bufferM));
    }

    // ── History snapshots ──────────────────────────────────────────────────────

    public Mono<TaxiHistoryResponse> getHistory(String start, String end, String zoneName) {
        OffsetDateTime startTime;
        OffsetDateTime endTime;
        try {
            startTime = OffsetDateTime.parse(start);
            endTime = OffsetDateTime.parse(end);
        } catch (Exception e) {
            return Mono.error(new ValidationException("Invalid start/end datetime format"));
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

        // With zone filter: use DatabaseClient for the join query
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

        return db.sql(sql)
                .bind("zoneName", zoneName)
                .bind("start", startTime)
                .bind("end", endTime)
                .map(row -> TaxiHistoryResponse.SnapshotEntry.builder()
                        .apiTimestamp(row.get("api_timestamp", OffsetDateTime.class))
                        .taxiCount(row.get("zone_taxi_count", Long.class).intValue())
                        .build())
                .all()
                .collectList()
                .map(entries -> TaxiHistoryResponse.builder().snapshots(entries).build());
    }

    // ── Recent activity ────────────────────────────────────────────────────────

    public Mono<Long> getRecentDelta(int minutes) {
        String sql = """
                SELECT MAX(taxi_count) - MIN(taxi_count) AS delta
                FROM taxi_snapshots
                WHERE fetched_at >= NOW() - INTERVAL ':minutes minutes'
                """.replace(":minutes", String.valueOf(minutes));

        return db.sql(sql)
                .map(row -> {
                    Long delta = row.get("delta", Long.class);
                    return delta != null ? delta : 0L;
                })
                .one()
                .defaultIfEmpty(0L);
    }
}
