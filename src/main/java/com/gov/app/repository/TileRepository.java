package com.gov.app.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/**
 * Repository for generating Mapbox Vector Tiles (MVT) from taxi_positions via PostGIS ST_AsMVT.
 */
@Repository
@RequiredArgsConstructor
public class TileRepository {

    private final DatabaseClient db;

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
                WHERE tp.snapshot_id = ANY(:snapshotIds)
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
                WHERE tp.snapshot_id = ANY(:snapshotIds)
                  AND ST_Intersects(
                        tp.geog::geometry,
                        ST_Transform(ST_TileEnvelope(:z, :x, :y), 4326)
                      )
            ) AS tile
            WHERE geom IS NOT NULL
            """;

    public Mono<byte[]> fetchTimelineTile(Long[] snapshotIds, int z, int x, int y, String zone) {
        DatabaseClient.GenericExecuteSpec spec = zone != null
                ? db.sql(TIMELINE_TILE_SQL_WITH_ZONE)
                        .bind("snapshotIds", snapshotIds)
                        .bind("z", z).bind("x", x).bind("y", y)
                        .bind("zone", zone)
                : db.sql(TIMELINE_TILE_SQL_NO_ZONE)
                        .bind("snapshotIds", snapshotIds)
                        .bind("z", z).bind("x", x).bind("y", y);

        return spec
                .map(row -> {
                    ByteBuffer buf = row.get("mvt", ByteBuffer.class);
                    if (buf == null) return new byte[0];
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    return bytes;
                })
                .one()
                .defaultIfEmpty(new byte[0]);
    }
}
