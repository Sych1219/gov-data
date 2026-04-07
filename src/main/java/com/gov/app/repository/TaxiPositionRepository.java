package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Raw-SQL repository for taxi_positions using NamedParameterJdbcTemplate.
 * Spring Data cannot derive queries with PostGIS functions, so all spatial
 * operations are expressed as native SQL here.
 */
@Repository
@RequiredArgsConstructor
public class TaxiPositionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Batch-inserts taxi positions for a snapshot using unnest for efficiency.
     */
    public void batchInsert(long snapshotId, OffsetDateTime apiTimestamp, List<Double> lons, List<Double> lats) {
        String sql = """
                INSERT INTO taxi_positions (snapshot_id, api_timestamp, longitude, latitude)
                SELECT ?, ?, unnest(?::float8[]), unnest(?::float8[])
                """;
        jdbcTemplate.execute((java.sql.Connection conn) -> {
            Array lonsArr = conn.createArrayOf("float8", lons.toArray());
            Array latsArr = conn.createArrayOf("float8", lats.toArray());
            try (var ps = conn.prepareStatement(sql)) {
                ps.setLong(1, snapshotId);
                ps.setObject(2, apiTimestamp);
                ps.setArray(3, lonsArr);
                ps.setArray(4, latsArr);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Count taxis within a radius (in metres) of the given point. */
    public long countNearby(long snapshotId, double lat, double lon, int radiusM) {
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("lat", lat)
                .addValue("lon", lon)
                .addValue("radiusM", radiusM);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    /** List taxis within a radius. Returns rows with longitude and latitude. */
    public List<double[]> listNearby(long snapshotId, double lat, double lon, int radiusM, int limit) {
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("lat", lat)
                .addValue("lon", lon)
                .addValue("radiusM", radiusM)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }

    /** Find N nearest taxis, ordered by distance. Returns [longitude, latitude, distance_m]. */
    public List<double[]> findNearest(long snapshotId, double lat, double lon, int limit) {
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("lat", lat)
                .addValue("lon", lon)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude"),
                rs.getDouble("distance_m")
        });
    }

    /** Count taxis within a named zone polygon. */
    public long countInZone(long snapshotId, String zoneName) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                JOIN zones z ON z.name = :zoneName
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Covers(z.geog, tp.geog)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("zoneName", zoneName);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    /** Count taxis within a custom GeoJSON polygon. */
    public long countInPolygon(long snapshotId, String polygonGeoJson) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Within(
                        tp.geog::geometry,
                        ST_GeomFromGeoJSON(:polygon)
                      )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("polygon", polygonGeoJson);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    /** Count taxis within buffer metres of a named road/highway. */
    public long countNearRoad(long snapshotId, String roadName, int bufferM) {
        String sql = """
                SELECT COUNT(*) AS cnt
                FROM taxi_positions tp
                JOIN zones z ON z.name = :roadName AND z.category IN ('road', 'highway')
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(tp.geog, z.geog, :bufferM)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("roadName", roadName)
                .addValue("bufferM", bufferM);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    /** Count taxis within buffer metres of a custom GeoJSON LineString route. */
    public long countAlongRoute(long snapshotId, String routeGeoJson, int bufferM) {
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("route", routeGeoJson)
                .addValue("bufferM", bufferM);
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result != null ? result : 0L;
    }

    /** List taxis within a named zone polygon. Returns rows with longitude and latitude. */
    public List<double[]> listInZone(long snapshotId, String zoneName) {
        String sql = """
                SELECT tp.longitude, tp.latitude
                FROM taxi_positions tp
                JOIN zones z ON z.name = :zoneName
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Covers(z.geog, tp.geog)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("zoneName", zoneName);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }

    /** List taxis within a custom GeoJSON polygon. Returns rows with longitude and latitude. */
    public List<double[]> listInPolygon(long snapshotId, String polygonGeoJson) {
        String sql = """
                SELECT tp.longitude, tp.latitude
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_Within(
                        tp.geog::geometry,
                        ST_GeomFromGeoJSON(:polygon)
                      )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("polygon", polygonGeoJson);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }

    /** List taxis within buffer metres of a named road/highway. Returns rows with longitude and latitude. */
    public List<double[]> listNearRoad(long snapshotId, String roadName, int bufferM) {
        String sql = """
                SELECT tp.longitude, tp.latitude
                FROM taxi_positions tp
                JOIN zones z ON z.name = :roadName AND z.category IN ('road', 'highway')
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(tp.geog, z.geog, :bufferM)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("roadName", roadName)
                .addValue("bufferM", bufferM);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }

    /** List all taxi positions for a given snapshot. Returns rows with longitude and latitude. */
    public List<double[]> listForSnapshot(long snapshotId) {
        String sql = """
                SELECT longitude, latitude
                FROM taxi_positions
                WHERE snapshot_id = :snapshotId
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }

    /** List taxis within buffer metres of a custom GeoJSON LineString route. Returns rows with longitude and latitude. */
    public List<double[]> listAlongRoute(long snapshotId, String routeGeoJson, int bufferM) {
        String sql = """
                SELECT tp.longitude, tp.latitude
                FROM taxi_positions tp
                WHERE tp.snapshot_id = :snapshotId
                  AND ST_DWithin(
                        tp.geog,
                        ST_GeomFromGeoJSON(:route)::geography,
                        :bufferM
                      )
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotId", snapshotId)
                .addValue("route", routeGeoJson)
                .addValue("bufferM", bufferM);
        return jdbc.query(sql, params, (rs, i) -> new double[]{
                rs.getDouble("longitude"),
                rs.getDouble("latitude")
        });
    }
}
