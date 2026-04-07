package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for generating Mapbox Vector Tiles (MVT) from taxi_positions via PostGIS ST_AsMVT.
 */
@Repository
@RequiredArgsConstructor
public class TileRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String TIMELINE_TILE_SQL_WITH_ZONE = """
            SELECT ST_AsMVT(tile, 'taxis', 4096, 'geom') AS mvt
            FROM (
                SELECT tp.id,
                       tp.snapshot_id,
                       ST_AsMVTGeom(
                           ST_Transform(tp.geog::geometry, 3857),
                           ST_TileEnvelope(:z, :x, :y),
                           4096, 256, true
                       ) AS geom
                FROM taxi_positions tp
                WHERE tp.snapshot_id IN (:snapshotIds)
                  AND ST_Intersects(
                        tp.geog::geometry,
                        ST_Transform(ST_TileEnvelope(:z, :x, :y), 4326)
                      )
                  AND ST_Covers(
                        (SELECT geog::geometry FROM zones WHERE name = :zone),
                        tp.geog::geometry
                      )
            ) AS tile
            WHERE geom IS NOT NULL
            """;

    private static final String TIMELINE_TILE_SQL_NO_ZONE = """
            SELECT ST_AsMVT(tile, 'taxis', 4096, 'geom') AS mvt
            FROM (
                SELECT tp.id,
                       tp.snapshot_id,
                       ST_AsMVTGeom(
                           ST_Transform(tp.geog::geometry, 3857),
                           ST_TileEnvelope(:z, :x, :y),
                           4096, 256, true
                       ) AS geom
                FROM taxi_positions tp
                WHERE tp.snapshot_id IN (:snapshotIds)
                  AND ST_Intersects(
                        tp.geog::geometry,
                        ST_Transform(ST_TileEnvelope(:z, :x, :y), 4326)
                      )
            ) AS tile
            WHERE geom IS NOT NULL
            """;

    public byte[] fetchTimelineTile(List<Long> snapshotIds, int z, int x, int y, String zone) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("snapshotIds", snapshotIds)
                .addValue("z", z)
                .addValue("x", x)
                .addValue("y", y);

        String sql;
        if (zone != null) {
            sql = TIMELINE_TILE_SQL_WITH_ZONE;
            params.addValue("zone", zone);
        } else {
            sql = TIMELINE_TILE_SQL_NO_ZONE;
        }

        List<byte[]> results = jdbc.query(sql, params, (rs, i) -> {
            byte[] mvt = rs.getBytes("mvt");
            return mvt != null ? mvt : new byte[0];
        });
        return results.isEmpty() ? new byte[0] : results.get(0);
    }
}
