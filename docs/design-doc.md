# Taxi Availability Service — Design Document

> **Cross-repo docs** — when updating this file, also check:
> - `civic-app` → `design-docs/MVP-taxi-spatial-qa.md` — consumes endpoints via OpenAPI toolkit; documents `data.type`/`context.type` unions (§7.1), query params (§6.3 UNITS), `{success, data, error}` envelope (§7.1), data model shapes (§7.1)
> - `civic-frontend` → `docs/apis-data-contract.md` — documents `POST /api/v1/query` endpoint, `data.type`/`context.type` unions, `{answer, data, metadata}` envelope, data model shapes, error codes, GeoJSON types
> - Full index: `civic-frontend/docs/cross-repo-index.md`

> **Stack**: Java 21 · Spring Boot 3.3.4 · WebFlux (reactive) · R2DBC · PostgreSQL + PostGIS · Lombok

---

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        gov-data Service                         │
│                                                                 │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │  Scheduler   │───▶│ TaxiFetchService │───▶│  WebClient    │──┼──▶ data.gov.sg API
│  │  (1 min)     │    │                  │    │               │  │
│  └──────────────┘    └────────┬─────────┘    └───────────────┘  │
│                               │ save                            │
│  ┌──────────────┐    ┌────────▼─────────┐                       │
│  │  REST Client │───▶│ TaxiQueryService │                       │
│  │  (HTTP)      │    │                  │                       │
│  └──────────────┘    └────────┬─────────┘                       │
│                               │ R2DBC                           │
│                      ┌────────▼─────────┐                       │
│                      │   PostgreSQL      │                       │
│                      │   + PostGIS       │                       │
│                      └──────────────────┘                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Data Ingestion Pipeline

### 2.1 Upstream API

| Item | Value |
|------|-------|
| Endpoint | `GET https://api.data.gov.sg/v1/transport/taxi-availability` |
| Optional param | `date_time` — `YYYY-MM-DD[T]HH:mm:ss` (SGT) |
| Response format | GeoJSON (`application/vnd.geo+json`) |
| Recommended poll interval | **10 minutes** |
| Auth | `x-api-key` header (optional, for higher rate limits) |

### 2.2 Scheduled Fetch Flow

```
Scheduler (every 60s)
  └─▶ TaxiFetchService.fetchAndSave()
        ├─▶ WebClient GET /transport/taxi-availability
        ├─▶ Parse GeoJSON → TaxiSnapshotEntity + List<TaxiPositionEntity>
        ├─▶ INSERT INTO taxi_snapshots
        └─▶ BATCH INSERT INTO taxi_positions
```

### 2.3 Response Mapping

The upstream GeoJSON is structured as:

```json
{
  "type": "FeatureCollection",
  "features": [{
    "geometry": {
      "type": "MultiPoint",
      "coordinates": [[103.123, 1.456], ...]
    },
    "properties": {
      "timestamp": "2026-02-28T08:00:00+08:00",
      "taxi_count": 7843
    }
  }]
}
```

Mapped to:
- **One `taxi_snapshots` row** per API call (metadata)
- **N `taxi_positions` rows** per snapshot (one per coordinate pair)

---

## 3. Database Design

### 3.1 PostGIS Setup

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

### 3.2 Tables

```sql
-- ── Snapshot metadata (one row per API poll) ────────────────────
CREATE TABLE IF NOT EXISTS taxi_snapshots (
    id            BIGSERIAL    PRIMARY KEY,
    api_timestamp TIMESTAMPTZ  NOT NULL,
    taxi_count    INT          NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_taxi_snapshots_api_timestamp
    ON taxi_snapshots (api_timestamp DESC);

-- ── Individual taxi positions (partitioned by api_timestamp, SGT-day aligned) ─
-- Partitioned by RANGE on api_timestamp (UTC). Each partition covers one SGT day:
--   SGT day YYYY-MM-DD = UTC (YYYY-MM-(DD-1) 16:00:00+00) to (YYYY-MM-DD 16:00:00+00)
-- NOTE: FK constraints are not supported on partitioned tables in PostgreSQL.
--       Referential integrity is enforced at the application layer.
-- NOTE: Daily partitions must be created manually before each SGT day starts.
--       The default partition below acts as a safety net for unmapped rows.
CREATE SEQUENCE IF NOT EXISTS taxi_positions_id_seq;

CREATE TABLE IF NOT EXISTS taxi_positions (
    id            BIGINT      NOT NULL DEFAULT nextval('taxi_positions_id_seq'),
    snapshot_id   BIGINT      NOT NULL,
    api_timestamp TIMESTAMPTZ NOT NULL,
    longitude     FLOAT8      NOT NULL,
    latitude      FLOAT8      NOT NULL,
    geog          GEOGRAPHY(POINT, 4326)
                      GENERATED ALWAYS AS (
                          ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                      ) STORED,
    PRIMARY KEY (id, api_timestamp)
) PARTITION BY RANGE (api_timestamp);

-- Safety net partition — catches rows with no matching daily partition
CREATE TABLE IF NOT EXISTS taxi_positions_default
    PARTITION OF taxi_positions DEFAULT;

CREATE INDEX IF NOT EXISTS idx_taxi_positions_snapshot_id
    ON taxi_positions (snapshot_id);
CREATE INDEX IF NOT EXISTS idx_taxi_positions_geog
    ON taxi_positions USING GIST (geog);
CREATE INDEX IF NOT EXISTS idx_taxi_positions_api_timestamp
    ON taxi_positions (api_timestamp DESC);

-- ── Pre-defined named zones (districts, roads, highways) ─────────────────────
CREATE TABLE IF NOT EXISTS zones (
    id       SERIAL  PRIMARY KEY,
    name     TEXT    NOT NULL UNIQUE,
    category TEXT    NOT NULL,   -- 'district' | 'road' | 'highway'
    geog     GEOGRAPHY NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_zones_name     ON zones (name);
CREATE INDEX IF NOT EXISTS idx_zones_name_trgm ON zones USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_zones_geog     ON zones USING GIST (geog);
```

### 3.3 Data Retention Strategy

| Concern | Approach |
|---------|----------|
| Raw volume | ~8 000 taxis × 144 polls/day (10-min interval) ≈ **1.15 M rows/day** |
| Retention | Keep last **7 days** of full-resolution data; older data is summarised |
| Cleanup job | Drop old daily partitions: `DROP TABLE taxi_positions_YYYY_MM_DD` — instant, no row-by-row delete |
| Partitioning | `taxi_positions` partitioned by `PARTITION BY RANGE (api_timestamp)` — one partition per SGT day (UTC boundary: `(DD-1) 16:00:00+00` to `DD 16:00:00+00`) |

---

## 4. API Endpoint Design

All endpoints accept an optional `datetime` query parameter (`YYYY-MM-DDTHH:mm:ss` SGT).
When omitted, the **latest** available snapshot is used.

Base path: `/api/v1/taxis`

---

### 4.0 Unified Response Structure

All endpoints return the same outer envelope:

```json
{ "success": true,  "data": { ... }, "error": null }
{ "success": false, "data": null,    "error": { "code": "NOT_FOUND", "message": "...", "details": {} } }
```

The `data` field is a **tagged union** — a `type` discriminator field determines the shape:

| `data.type` | Used by |
|---|---|
| `spatial_query` | `/nearby`, `/nearest`, `/zone/*/count`, `/polygon/count`, `/road/*/count`, `/route/count` |
| `timeline` | `/history/snapshots`, `/history/recent` |
| `zone_geometry` | `/zones/*/geometry` |

Both `spatial_query` and `timeline` carry an optional `context` object that captures the query parameters, discriminated by `context.type` (for `timeline`, `context` is present when a zone/spatial filter was applied, `null` otherwise):

| `context.type` | Endpoint |
|---|---|
| `radius` | `/nearby` |
| `nearest` | `/nearest` |
| `zone` | `/zone/*/count` |
| `polygon` | `/polygon/count` |
| `road` | `/road/*/count` |
| `route` | `/route/count` |

---

### 4.1 📍 Radius Query — Taxis within a radius

```
GET /api/v1/taxis/nearby
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `lat` | number | ✅ | Centre latitude |
| `lon` | number | ✅ | Centre longitude |
| `radius` | integer | ✅ | Radius in **metres** |
| `datetime` | string | ❌ | Target time (SGT) |
| `limit` | integer | ❌ | Max locations returned, default `100` |

**Core SQL**:
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
JOIN taxi_snapshots ts ON tp.snapshot_id = ts.id
WHERE ts.id = :snapshotId
  AND ST_DWithin(
        tp.geog,
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
        :radiusMetres
      );
```

**Example**: `GET /api/v1/taxis/nearby?lat=1.3644&lon=103.9915&radius=3000`
→ taxis within 3 km of Changi Airport

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 42,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": { "type": "radius", "lat": 1.3644, "lon": 103.9915, "radius_m": 3000 },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.992, 1.361] }, "properties": null },
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.987, 1.365] }, "properties": null }
      ]
    }
  },
  "error": null
}
```

---

### 4.2 📍 Nearest N Taxis

```
GET /api/v1/taxis/nearest
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `lat` | number | ✅ | Query latitude |
| `lon` | number | ✅ | Query longitude |
| `limit` | integer | ❌ | Number of results, default `5` |
| `datetime` | string | ❌ | Target time |

**Core SQL**:
```sql
SELECT longitude, latitude,
       ST_Distance(
           tp.geog,
           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
       ) AS distance_m
FROM taxi_positions tp
WHERE snapshot_id = :snapshotId
ORDER BY tp.geog <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
LIMIT :limit;
```

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 2,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": { "type": "nearest", "lat": 1.3521, "lon": 103.8198, "limit": 5 },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.820, 1.352] }, "properties": { "distance_m": 123.4 } },
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.821, 1.353] }, "properties": { "distance_m": 201.0 } }
      ]
    }
  },
  "error": null
}
```

---

### 4.3 🗺 Zone / District Query

```
GET /api/v1/taxis/zone/count?zoneName={zoneName}&datetime={datetime}
```

| Query Param | Type | Required | Description |
|-------------|------|----------|-------------|
| `zoneName` | string | ✅ | Pre-defined zone name, e.g. `tampines`, `jurong-west`, `cbd` |
| `datetime` | string | ❌ | Target time |

**Core SQL**:
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
JOIN zones z ON z.name = :zoneName
WHERE tp.snapshot_id = :snapshotId
  AND ST_Covers(z.geog, tp.geog);
```

**Example**: `GET /api/v1/taxis/zone/count?zoneName=tampines`

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 187,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": { "type": "zone", "zone_name": "tampines", "category": "district" },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.820, 1.352] }, "properties": null },
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.821, 1.353] }, "properties": null }
      ]
    }
  },
  "error": null
}
```

---

### 4.4 🗺 Custom Polygon Query

```
POST /api/v1/taxis/polygon/count
```

**Request body**:
```json
{
  "polygon": {
    "type": "Polygon",
    "coordinates": [[[103.81, 1.28], [103.85, 1.28], [103.85, 1.32], [103.81, 1.32], [103.81, 1.28]]]
  },
  "datetime": "2026-02-28T08:00:00"
}
```

**Core SQL**:
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
WHERE tp.snapshot_id = :snapshotId
  AND ST_Covers(ST_GeomFromGeoJSON(:polygonGeoJson)::geography, tp.geog);
```

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 23,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": {
      "type": "polygon",
      "polygon": {
        "type": "Polygon",
        "coordinates": [[[103.81, 1.28], [103.85, 1.28], [103.85, 1.32], [103.81, 1.32], [103.81, 1.28]]]
      }
    },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.830, 1.290] }, "properties": null }
      ]
    }
  },
  "error": null
}
```

---

### 4.5 🛣 Road / Highway Query

```
GET /api/v1/taxis/road/count?roadName={roadName}&buffer_m={buffer_m}&datetime={datetime}
```

| Query Param | Type | Required | Description |
|-------------|------|----------|-------------|
| `roadName` | string | ✅ | Pre-loaded road name, e.g. `orchard-road`, `pie`, `cte` |
| `buffer_m` | integer | ❌ | Buffer around road in metres, default `100` |
| `datetime` | string | ❌ | Target time |

**Core SQL** (road stored as `LineString` in `zones`):
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
JOIN zones z ON z.name = :roadName AND z.category IN ('road', 'highway')
WHERE tp.snapshot_id = :snapshotId
  AND ST_DWithin(
        tp.geog,
        z.geog,
        :bufferMetres
      );
```

**Example**: `GET /api/v1/taxis/road/count?roadName=orchard-road&buffer_m=150`

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 15,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": { "type": "road", "road_name": "orchard-road", "category": "highway", "buffer_m": 150 },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.832, 1.304] }, "properties": null }
      ]
    }
  },
  "error": null
}
```

---

### 4.6 🚗 Route Buffer Query

```
POST /api/v1/taxis/route/count
```

**Request body**:
```json
{
  "route": {
    "type": "LineString",
    "coordinates": [[103.989, 1.364], [103.851, 1.290], [103.833, 1.280]]
  },
  "buffer_m": 200,
  "datetime": "2026-02-28T08:00:00"
}
```

**Core SQL**:
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
WHERE tp.snapshot_id = :snapshotId
  AND ST_DWithin(
        tp.geog,
        ST_GeomFromGeoJSON(:routeGeoJson)::geography,   -- cast geometry → geography
        :bufferMetres
      );
```

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "spatial_query",
    "taxi_count": 28,
    "snapshot_time": "2026-02-28T08:00:00+08:00",
    "context": {
      "type": "route",
      "route": {
        "type": "LineString",
        "coordinates": [[103.989, 1.364], [103.851, 1.290], [103.833, 1.280]]
      },
      "buffer_m": 200
    },
    "locations": {
      "type": "FeatureCollection",
      "features": [
        { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.851, 1.290] }, "properties": null }
      ]
    }
  },
  "error": null
}
```

---

### 4.7 🕐 Time Range Query (MVT-backed)

The timeline endpoint is split into two parts: a **metadata endpoint** that returns lightweight snapshot metadata (no geometry), and a **tile endpoint** that serves each snapshot's taxi positions as Mapbox Vector Tiles (MVT). This avoids sending massive GeoJSON payloads when the time range is long.

#### 4.7.1 Timeline Metadata

```
GET /api/v1/taxis/history/snapshots
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `start` | string | ✅ | Start time (SGT) |
| `end` | string | ✅ | End time (SGT) |
| `zone` | string | ❌ | Filter to a named zone |

Returns snapshot metadata only — `snapshot_id`, `timestamp`, and `taxi_count` — **without** `locations`. When `zone` is provided, `taxi_count` reflects only the taxis within that zone.

**Core SQL** (no zone):
```sql
SELECT ts.id AS snapshot_id, ts.api_timestamp, ts.taxi_count
FROM taxi_snapshots ts
WHERE ts.api_timestamp BETWEEN :start AND :end
ORDER BY ts.api_timestamp;
```

With optional zone filter:
```sql
SELECT ts.id AS snapshot_id, ts.api_timestamp, COUNT(tp.id) AS taxi_count
FROM taxi_snapshots ts
JOIN taxi_positions tp ON tp.snapshot_id = ts.id
JOIN zones z ON z.name = :zoneName
WHERE ts.api_timestamp BETWEEN :start AND :end
  AND ST_Covers(z.geog, tp.geog)
GROUP BY ts.id, ts.api_timestamp
ORDER BY ts.api_timestamp;
```

**Example**: `GET /api/v1/taxis/history/snapshots?start=2026-02-28T08:00:00+08:00&end=2026-02-28T09:00:00+08:00&zone=cbd`

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "timeline",
    "from_time": "2026-02-28T08:00:00+08:00",
    "to_time": "2026-02-28T09:00:00+08:00",
    "context": { "type": "zone", "zone_name": "cbd", "category": "district" },
    "snapshots": [
      { "snapshot_id": 1001, "timestamp": "2026-02-28T08:00:00+08:00", "taxi_count": 3200 },
      { "snapshot_id": 1002, "timestamp": "2026-02-28T08:01:00+08:00", "taxi_count": 3215 }
    ]
  },
  "error": null
}
```

> **Note**: `snapshots[].locations` is no longer included. The frontend fetches spatial data per-snapshot via the tile endpoint (§4.7.2).

#### 4.7.2 Snapshot Tile Endpoint (MVT)

```
GET /api/v1/tiles/taxis/{snapshotId}/{z}/{x}/{y}.pbf
```

| Param | Type | Source | Description |
|-------|------|--------|-------------|
| `snapshotId` | long | path | Snapshot ID from the metadata response |
| `z` | int | path | Tile zoom level (0–22) |
| `x` | int | path | Tile column index |
| `y` | int | path | Tile row index |
| `zone` | string | query (optional) | Filter positions to a named zone |

Returns binary MVT (`application/x-protobuf`). The `z`, `x`, `y` parameters follow the standard [slippy map tile](https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames) convention — Mapbox GL JS computes these automatically based on the user's viewport and zoom level.

**Response headers**:
- `Content-Type: application/x-protobuf`
- `Cache-Control: max-age=300` (5 min — snapshot data is immutable)

**Core SQL**:
```sql
SELECT ST_AsMVT(tile, 'taxis', 4096, 'geom') AS mvt
FROM (
    SELECT tp.id,
           ST_AsMVTGeom(
               tp.geog::geometry,
               ST_TileEnvelope(:z, :x, :y),
               4096, 256, true
           ) AS geom
    FROM taxi_positions tp
    WHERE tp.snapshot_id = :snapshotId
      AND ST_Intersects(
            tp.geog::geometry,
            ST_Transform(ST_TileEnvelope(:z, :x, :y), 4326)
          )
      AND (:zone IS NULL OR ST_Covers(
            (SELECT geog::geometry FROM zones WHERE name = :zone),
            tp.geog::geometry
          ))
) AS tile
WHERE geom IS NOT NULL;
```

**Frontend usage** — Mapbox GL JS automatically requests the tiles it needs:
```typescript
map.addSource('timeline-taxis', {
  type: 'vector',
  tiles: [`${baseUrl}/tiles/taxis/${snapshotId}/{z}/{x}/{y}.pbf?zone=cbd`],
});

map.addLayer({
  id: 'taxi-heatmap',
  type: 'heatmap',
  source: 'timeline-taxis',
  'source-layer': 'taxis',
  // ...paint properties
});
```

When the user scrubs the timeline slider, the frontend swaps the tile source URL to point to the new `snapshotId`. Adjacent snapshots can be prefetched for smooth playback.

**MVT layer name**: `taxis` (matches the second argument to `ST_AsMVT`)

---

### 4.8 🕐 Recent Activity

```
GET /api/v1/taxis/history/recent
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `minutes` | integer | ❌ | Lookback window, default `15` |

Returns per-snapshot taxi positions across the lookback window. Designed for Mapbox timeline visualisation — the client loads the response once, then swaps `locations` on each slider tick.

**Response**:
```json
{
  "success": true,
  "data": {
    "type": "timeline",
    "from_time": "2026-02-28T08:00:00+08:00",
    "to_time": "2026-02-28T08:15:00+08:00",
    "window_minutes": 15,
    "snapshots": [
      {
        "timestamp": "2026-02-28T08:00:00+08:00",
        "taxi_count": 3200,
        "locations": {
          "type": "FeatureCollection",
          "features": [
            { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.832, 1.304] }, "properties": null },
            { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.851, 1.290] }, "properties": null }
          ]
        }
      },
      {
        "timestamp": "2026-02-28T08:01:00+08:00",
        "taxi_count": 3230,
        "locations": {
          "type": "FeatureCollection",
          "features": [
            { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.833, 1.305] }, "properties": null }
          ]
        }
      }
    ]
  },
  "error": null
}
```

---

### 4.9 🗺 Zone Geometry

```
GET /api/v1/zones/{name}/geometry
```

| Path Param | Description |
|------------|-------------|
| `name` | Zone name (fuzzy-matched), e.g. `tampines`, `aye`, `orchard-road` |

Returns a GeoJSON `Feature` with the boundary or path of the zone. **Districts** return a `Polygon`; **roads and highways** return a `LineString`.

Zone geometry is static — the response includes `Cache-Control: max-age=86400`. Clients should cache it and only re-fetch taxi count data on each poll.

**Example**: `GET /api/v1/zones/tampines/geometry`

**Response (district → Polygon)**:
```json
{
  "success": true,
  "data": {
    "type": "zone_geometry",
    "name": "tampines",
    "category": "district",
    "geometry": {
      "type": "Polygon",
      "coordinates": [[[103.80, 1.34], [103.85, 1.34], [103.85, 1.37], [103.80, 1.37], [103.80, 1.34]]]
    }
  },
  "error": null
}
```

**Example**: `GET /api/v1/zones/aye/geometry`

**Response (highway → LineString)**:
```json
{
  "success": true,
  "data": {
    "type": "zone_geometry",
    "name": "aye",
    "category": "highway",
    "geometry": {
      "type": "LineString",
      "coordinates": [[103.74, 1.28], [103.76, 1.29], [103.80, 1.30]]
    }
  },
  "error": null
}
```

---

### 4.10 Endpoint Summary Table

| # | Method | Path | Response | Description |
|---|--------|------|----------|-------------|
| 1 | GET | `/api/v1/taxis/nearby` | JSON | Taxis within radius (count + GeoJSON) |
| 2 | GET | `/api/v1/taxis/nearest` | JSON | Nearest N taxis with distance |
| 3 | GET | `/api/v1/taxis/zone/count?zoneName=` | JSON | Count in named zone |
| 4 | POST | `/api/v1/taxis/polygon/count` | JSON | Count in custom GeoJSON polygon |
| 5 | GET | `/api/v1/taxis/road/count?roadName=` | JSON | Count near road/highway |
| 6 | POST | `/api/v1/taxis/route/count` | JSON | Count along a route buffer |
| 7 | GET | `/api/v1/taxis/history/snapshots` | JSON | Timeline metadata (snapshot IDs + counts, no geometry) |
| 8 | GET | `/api/v1/tiles/taxis/{snapshotId}/{z}/{x}/{y}.pbf` | MVT | Taxi positions for a snapshot as vector tiles |
| 9 | GET | `/api/v1/taxis/history/recent` | JSON | Recently online taxi delta |
| 10 | GET | `/api/v1/zones/{name}/geometry` | JSON | GeoJSON geometry of a zone/road/highway |

---

## 5. Package / Class Structure

```
com.gov.app
├── config
│   ├── WebClientConfig.java          # WebClient bean for data.gov.sg
│   └── SchedulerConfig.java          # Enable scheduling
│
├── scheduler
│   └── TaxiDataScheduler.java        # @Scheduled every 60s → TaxiFetchService
│
├── service
│   ├── TaxiFetchService.java         # Calls upstream API, persists snapshot
│   ├── TaxiQueryService.java         # All spatial / temporal query logic
│   └── TileService.java              # Generates MVT tiles via PostGIS ST_AsMVT
│
├── controller
│   ├── TaxiController.java           # REST endpoints (WebFlux @RestController)
│   └── TileController.java           # MVT tile endpoints (application/x-protobuf)
│
├── domain
│   └── record
│       ├── TaxiSnapshot.java         # R2DBC entity → taxi_snapshots
│       └── TaxiPosition.java         # R2DBC entity → taxi_positions
│
├── dto
│   ├── response
│   │   ├── ApiResponse.java          # Generic envelope: success / data / error
│   │   ├── ApiError.java             # Error payload: code / message / details
│   │   ├── TaxiResponseData.java     # @JsonTypeInfo sealed interface for data union
│   │   ├── SpatialQueryData.java     # data.type = "spatial_query"
│   │   ├── TimelineData.java         # data.type = "timeline"
│   │   ├── ZoneGeometryData.java     # data.type = "zone_geometry"
│   │   └── SnapshotEntry.java        # Item inside TimelineData.snapshots
│   ├── context
│   │   ├── QueryContext.java         # @JsonTypeInfo sealed interface for context union
│   │   ├── RadiusContext.java        # context.type = "radius"
│   │   ├── NearestContext.java       # context.type = "nearest"
│   │   ├── ZoneContext.java          # context.type = "zone"
│   │   ├── PolygonContext.java       # context.type = "polygon"
│   │   ├── RoadContext.java          # context.type = "road"
│   │   └── RouteContext.java         # context.type = "route"
│   ├── request
│   │   ├── TaxiPolygonRequest.java
│   │   └── TaxiRouteRequest.java
│   └── upstream
│       └── GovTaxiResponse.java      # Maps data.gov.sg GeoJSON response
│
├── repository
│   ├── TaxiSnapshotRepository.java   # ReactiveCrudRepository
│   ├── TaxiPositionRepository.java   # Custom @Query with PostGIS functions
│   └── TileRepository.java           # MVT tile generation queries (ST_AsMVT)
│
└── exception
    ├── BusinessException.java
    ├── NotFoundException.java
    ├── UpstreamException.java
    ├── ValidationException.java
    └── GlobalExceptionHandler.java
```

---

## 6. Key Technical Decisions

### 6.1 PostGIS for Spatial Queries

R2DBC does not natively support PostGIS geometry types. Two options:

| Option | Approach | Trade-off |
|--------|----------|-----------|
| **A (Recommended)** | Store `longitude` / `latitude` as plain doubles; use raw `@Query` with `ST_*` functions returning primitive results (count, distance) | Simple R2DBC mapping; PostGIS power fully available |
| B | Use `io.r2dbc:r2dbc-postgresql` with custom codec for `geometry` type | More complex codec setup but enables full geometry return |

**Decision**: Option A — execute PostGIS queries via `DatabaseClient` with native SQL; return counts/coordinates as primitives.

### 6.2 Snapshot Resolution Strategy

When `datetime` is provided:
```sql
SELECT id FROM taxi_snapshots
WHERE api_timestamp <= :targetTime
ORDER BY api_timestamp DESC
LIMIT 1;
```
Returns the latest snapshot **at or before** the requested time.

### 6.3 Batch Insert for Positions

Use `DatabaseClient.inConnectionMany()` for bulk insert of taxi positions per snapshot to avoid N individual R2DBC inserts:

```java
// Bulk insert with unnest — api_timestamp required as partition key
INSERT INTO taxi_positions (snapshot_id, api_timestamp, longitude, latitude)
SELECT :snapshotId, :apiTimestamp, unnest(:lons::float8[]), unnest(:lats::float8[])
```

### 6.4 MVT Tile Generation via R2DBC

`ST_AsMVT` returns `bytea` from PostGIS. With R2DBC, the result is mapped to `ByteBuffer`:

```java
public Mono<byte[]> fetchTile(long snapshotId, int z, int x, int y, String zone) {
    return client.sql(TILE_SQL)
        .bind("snapshotId", snapshotId)
        .bind("z", z).bind("x", x).bind("y", y)
        .bind("zone", zone)  // null when unfiltered
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
```

Tiles are immutable per snapshot, so responses include `Cache-Control: max-age=300` to enable browser and CDN caching.

### 6.5 Zone Data Seeding

Pre-load Singapore zones/roads from publicly available GeoJSON sources (e.g. OneMap, OSM) into the `zones` table via a `DataInitializer` component on startup.

---

## 7. Configuration Additions

`application.yml` additions needed:

```yaml
gov-api:
  taxi:
    fetch-interval-ms: 60000
    base-url: https://api.data.gov.sg/v1
    api-key: ${GOV_API_KEY:}          # optional env var

spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypass
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

---

## 8. Error Handling

All errors are returned inside the standard envelope (`success: false`, `data: null`):

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "NOT_FOUND",
    "message": "Zone 'xyz' not found",
    "details": { "field": "zoneName", "value": "xyz" }
  }
}
```

| Scenario | HTTP Status | `error.code` | Exception class |
|----------|-------------|--------------|-----------------|
| Upstream API unavailable | 502 Bad Gateway | `UPSTREAM_ERROR` | `UpstreamException` |
| Unknown zone name | 404 Not Found | `NOT_FOUND` | `NotFoundException` |
| Invalid coordinates / radius ≤ 0 | 400 Bad Request | `VALIDATION_ERROR` | `ValidationException` |
| No snapshot available at `datetime` | 404 Not Found | `NOT_FOUND` | `NotFoundException` |
| Malformed request body | 400 Bad Request | `BAD_REQUEST` | `BusinessException` |

`GlobalExceptionHandler` maps each exception to `ApiResponse.fail(ApiError)` — the frontend always reads `res.success` first, then `res.error.code` to decide how to display the error.

---

## 9. Out of Scope (Future Work)

- **Real-time streaming** via WebSocket / SSE for live taxi position updates
- **Heatmap aggregation** endpoint (grid-based density)
- **Predictive availability** using historical time-series data
- **Authentication / rate limiting** on query endpoints
- **Caching layer** (Redis) for repeated identical spatial queries
