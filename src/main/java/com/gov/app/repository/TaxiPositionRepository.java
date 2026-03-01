package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Raw-SQL repository for taxi_positions using DatabaseClient.
 * Spring Data cannot derive queries with PostGIS functions, so all spatial
 * operations are expressed as native SQL here.
 */
@Repository
@RequiredArgsConstructor
public class TaxiPositionRepository {

    private final DatabaseClient db;

    /**
     * Batch-inserts taxi positions for a snapshot using unnest for efficiency.
     */
    public Mono<Void> batchInsert(long snapshotId, List<Double> lons, List<Double> lats) {
        Double[] lonsArr = lons.toArray(new Double[0]);
        Double[] latsArr = lats.toArray(new Double[0]);

        String sql = """
                INSERT INTO taxi_positions (snapshot_id, longitude, latitude)
                SELECT :snapshotId, unnest(:lons::float8[]), unnest(:lats::float8[])
                """;

        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("lons", lonsArr)
                .bind("lats", latsArr)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /** Count taxis within a radius (in metres) of the given point. */
    public Mono<Long> countNearby(long snapshotId, double lat, double lon, int radiusM) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(
                        tp.geog,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :radiusM
                      )
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("lat", lat)
                .bind("lon", lon)
                .bind("radiusM", radiusM)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    /** List taxis within a radius. Returns rows with longitude and latitude. */
    public Flux<double[]> listNearby(long snapshotId, double lat, double lon, int radiusM, int limit) {
        String sql = """
                SELECT tp.longitude, tp.latitude
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(
                        tp.geog,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                        :radiusM
                      )
                LIMIT :limit
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("lat", lat)
                .bind("lon", lon)
                .bind("radiusM", radiusM)
                .bind("limit", limit)
                .map(row -> new double[]{
                        row.get("longitude", Double.class),
                        row.get("latitude", Double.class)
                })
                .all();
    }

    /** Find N nearest taxis, ordered by distance. Returns [longitude, latitude, distance_m]. */
    public Flux<double[]> findNearest(long snapshotId, double lat, double lon, int limit) {
        String sql = """
                SELECT tp.longitude, tp.latitude,
                       ST_Distance(
                           tp.geog,
                           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                       ) AS distance_m
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                ORDER BY tp.geog <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                LIMIT :limit
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("lat", lat)
                .bind("lon", lon)
                .bind("limit", limit)
                .map(row -> new double[]{
                        row.get("longitude", Double.class),
                        row.get("latitude", Double.class),
                        row.get("distance_m", Double.class)
                })
                .all();
    }

    /** Count taxis within a named zone polygon. */
    public Mono<Long> countInZone(long snapshotId, String zoneName) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                JOIN zones z ON z.name = :zoneName
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Within(tp.geog::geometry, z.geog::geometry)
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("zoneName", zoneName)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    /** Count taxis within a custom GeoJSON polygon. */
    public Mono<Long> countInPolygon(long snapshotId, String polygonGeoJson) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Within(
                        tp.geog::geometry,
                        ST_GeomFromGeoJSON(:polygon)
                      )
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("polygon", polygonGeoJson)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    /** Count taxis within buffer metres of a named road/highway. */
    public Mono<Long> countNearRoad(long snapshotId, String roadName, int bufferM) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                JOIN zones z ON z.name = :roadName AND z.category IN ('road', 'highway')
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(tp.geog, z.geog, :bufferM)
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("roadName", roadName)
                .bind("bufferM", bufferM)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }

    /** Count taxis within buffer metres of a custom GeoJSON LineString route. */
    public Mono<Long> countAlongRoute(long snapshotId, String routeGeoJson, int bufferM) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(
                        tp.geog,
                        ST_GeomFromGeoJSON(:route)::geography,
                        :bufferM
                      )
                """;
        return db.sql(sql)
                .bind("snapshotId", snapshotId)
                .bind("route", routeGeoJson)
                .bind("bufferM", bufferM)
                .map(row -> row.get("cnt", Long.class))
                .one();
    }
}
