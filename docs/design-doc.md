# Taxi Availability Service — Design Document

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
| Recommended poll interval | **1 minute** |
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
CREATE TABLE taxi_snapshots (
    id           BIGSERIAL PRIMARY KEY,
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),   -- when we polled
    api_timestamp TIMESTAMPTZ NOT NULL,                -- LTA data timestamp
    taxi_count   INTEGER     NOT NULL
);

CREATE INDEX idx_snapshots_fetched_at ON taxi_snapshots (fetched_at DESC);

-- ── Individual taxi positions ────────────────────────────────────
CREATE TABLE taxi_positions (
    id          BIGSERIAL PRIMARY KEY,
    snapshot_id BIGINT  NOT NULL REFERENCES taxi_snapshots(id) ON DELETE CASCADE,
    longitude   DOUBLE PRECISION NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    location    GEOMETRY(POINT, 4326) NOT NULL    -- PostGIS spatial column
                GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)) STORED
);

CREATE INDEX idx_positions_location    ON taxi_positions USING GIST(location);
CREATE INDEX idx_positions_snapshot_id ON taxi_positions (snapshot_id);

-- ── Pre-defined named zones (Singapore districts / roads) ────────
CREATE TABLE zones (
    id       SERIAL PRIMARY KEY,
    name     VARCHAR(100) UNIQUE NOT NULL,   -- e.g. 'tampines', 'cbd', 'orchard-road'
    category VARCHAR(20)  NOT NULL,          -- 'district' | 'road' | 'highway'
    boundary GEOMETRY(GEOMETRY, 4326) NOT NULL  -- Polygon or LineString
);

CREATE INDEX idx_zones_boundary ON zones USING GIST(boundary);
CREATE INDEX idx_zones_name     ON zones (name);
```

### 3.3 Data Retention Strategy

| Concern | Approach |
|---------|----------|
| Raw volume | ~8 000 taxis × 1440 polls/day ≈ **11.5 M rows/day** |
| Retention | Keep last **7 days** of full-resolution data; older data is summarised |
| Cleanup job | Daily scheduled task: `DELETE FROM taxi_positions WHERE snapshot_id IN (SELECT id FROM taxi_snapshots WHERE fetched_at < NOW() - INTERVAL '7 days')` |
| Partitioning | Partition `taxi_positions` by day (`PARTITION BY RANGE (snapshot_id)`) for fast pruning |

---

## 4. API Endpoint Design

All endpoints accept an optional `datetime` query parameter (`YYYY-MM-DDTHH:mm:ss` SGT).  
When omitted, the **latest** available snapshot is used.

Base path: `/api/v1/taxis`

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
        tp.location::geography,
        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
        :radiusMetres
      );
```

**Example**: `GET /api/v1/taxis/nearby?lat=1.3644&lon=103.9915&radius=3000`
→ taxis within 3 km of Changi Airport

**Response**:
```json
{
  "taxi_count": 42,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "query": { "lat": 1.3644, "lon": 103.9915, "radius_m": 3000 },
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.992, 1.361] }, "properties": null },
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.987, 1.365] }, "properties": null }
    ]
  }
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
           tp.location::geography,
           ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
       ) AS distance_m
FROM taxi_positions tp
WHERE snapshot_id = :snapshotId
ORDER BY tp.location <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)
LIMIT :limit;
```

**Response**:
```json
{
  "taxi_count": 2,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "query": { "lat": 1.3521, "lon": 103.8198, "limit": 5 },
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.820, 1.352] }, "properties": { "distance_m": 123.4 } },
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.821, 1.353] }, "properties": { "distance_m": 201.0 } }
    ]
  }
}
```

---

### 4.3 🗺 Zone / District Query

```
GET /api/v1/taxis/zone/{zoneName}/count
```

| Path Param | Description |
|------------|-------------|
| `zoneName` | Pre-defined zone name, e.g. `tampines`, `jurong-west`, `cbd` |

| Query Param | Type | Required | Description |
|-------------|------|----------|-------------|
| `datetime` | string | ❌ | Target time |

**Core SQL**:
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
JOIN zones z ON z.name = :zoneName
WHERE tp.snapshot_id = :snapshotId
  AND ST_Within(tp.location, z.boundary);
```

**Example**: `GET /api/v1/taxis/zone/tampines/count`

**Response**:
```json
{
  "zone": "tampines",
  "taxi_count": 187,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.820, 1.352] }, "properties": null },
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.821, 1.353] }, "properties": null }
    ]
  }
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
  AND ST_Within(tp.location, ST_GeomFromGeoJSON(:polygonGeoJson));
```

**Response**:
```json
{
  "taxi_count": 23,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.830, 1.290] }, "properties": null }
    ]
  }
}
```

---

### 4.5 🛣 Road / Highway Query

```
GET /api/v1/taxis/road/{roadName}/count
```

| Path Param | Description |
|------------|-------------|
| `roadName` | Pre-loaded road name, e.g. `orchard-road`, `pie`, `cte` |

| Query Param | Type | Required | Description |
|-------------|------|----------|-------------|
| `buffer_m` | integer | ❌ | Buffer around road in metres, default `100` |
| `datetime` | string | ❌ | Target time |

**Core SQL** (road stored as `LineString` in `zones`):
```sql
SELECT COUNT(*) AS taxi_count
FROM taxi_positions tp
JOIN zones z ON z.name = :roadName AND z.category IN ('road', 'highway')
WHERE tp.snapshot_id = :snapshotId
  AND ST_DWithin(
        tp.location::geography,
        z.boundary::geography,
        :bufferMetres
      );
```

**Example**: `GET /api/v1/taxis/road/orchard-road/count?buffer_m=150`

**Response**:
```json
{
  "road": "orchard-road",
  "taxi_count": 15,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.832, 1.304] }, "properties": null }
    ]
  }
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
        tp.location::geography,
        ST_GeomFromGeoJSON(:routeGeoJson)::geography,
        :bufferMetres
      );
```

**Response**:
```json
{
  "taxi_count": 28,
  "buffer_m": 200,
  "snapshot_time": "2026-02-28T08:00:00+08:00",
  "locations": {
    "type": "FeatureCollection",
    "features": [
      { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.851, 1.290] }, "properties": null }
    ]
  }
}
```

---

### 4.7 🕐 Time Range Query

```
GET /api/v1/taxis/history/snapshots
```

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `start` | string | ✅ | Start time (SGT) |
| `end` | string | ✅ | End time (SGT) |
| `zone` | string | ❌ | Filter to a named zone |

Returns per-snapshot taxi positions within the time window. Same structure as **4.8** — designed for Mapbox timeline visualisation.

**Core SQL** (no zone):
```sql
SELECT ts.api_timestamp, ts.taxi_count
FROM taxi_snapshots ts
WHERE ts.api_timestamp BETWEEN :start AND :end
ORDER BY ts.api_timestamp;
```

With optional zone filter:
```sql
SELECT ts.api_timestamp, COUNT(tp.id) AS zone_taxi_count
FROM taxi_snapshots ts
JOIN taxi_positions tp ON tp.snapshot_id = ts.id
JOIN zones z ON z.name = :zoneName
WHERE ts.api_timestamp BETWEEN :start AND :end
  AND ST_Within(tp.location, z.boundary)
GROUP BY ts.api_timestamp
ORDER BY ts.api_timestamp;
```

**Example**: `GET /api/v1/taxis/history/snapshots?start=2026-02-28T08:00:00+08:00&end=2026-02-28T09:00:00+08:00&zone=cbd`

**Response**:
```json
{
  "from_time": "2026-02-28T08:00:00+08:00",
  "to_time": "2026-02-28T09:00:00+08:00",
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
      "taxi_count": 3215,
      "locations": {
        "type": "FeatureCollection",
        "features": [
          { "type": "Feature", "geometry": { "type": "Point", "coordinates": [103.833, 1.305] }, "properties": null }
        ]
      }
    }
  ]
}
```

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
  "window_minutes": 15,
  "from_time": "2026-02-28T08:00:00+08:00",
  "to_time": "2026-02-28T08:15:00+08:00",
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
}
```

**Frontend usage (Mapbox)**:
```js
// Load once
map.addSource('taxis', { type: 'geojson', data: response.snapshots[0].locations });

// On timeline slide
const snap = response.snapshots[sliderIndex];
map.getSource('taxis').setData(snap.locations);
countLabel.text = snap.taxi_count;
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
  "type": "Feature",
  "properties": {
    "name": "tampines",
    "category": "district"
  },
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[103.80, 1.34], [103.85, 1.34], [103.85, 1.37], [103.80, 1.37], [103.80, 1.34]]]
  }
}
```

**Example**: `GET /api/v1/zones/aye/geometry`

**Response (highway → LineString)**:
```json
{
  "type": "Feature",
  "properties": {
    "name": "aye",
    "category": "highway"
  },
  "geometry": {
    "type": "LineString",
    "coordinates": [[103.74, 1.28], [103.76, 1.29], [103.80, 1.30]]
  }
}
```

---

### 4.10 Endpoint Summary Table

| # | Method | Path | Description |
|---|--------|------|-------------|
| 1 | GET | `/api/v1/taxis/nearby` | Taxis within radius (count + GeoJSON) |
| 2 | GET | `/api/v1/taxis/nearest` | Nearest N taxis with distance |
| 3 | GET | `/api/v1/taxis/zone/{zoneName}/count` | Count in named zone |
| 4 | POST | `/api/v1/taxis/polygon/count` | Count in custom GeoJSON polygon |
| 5 | GET | `/api/v1/taxis/road/{roadName}/count` | Count near road/highway |
| 6 | POST | `/api/v1/taxis/route/count` | Count along a route buffer |
| 7 | GET | `/api/v1/taxis/history/snapshots` | Time-range taxi count history |
| 8 | GET | `/api/v1/taxis/history/recent` | Recently online taxi delta |
| 9 | GET | `/api/v1/zones/{name}/geometry` | GeoJSON geometry of a zone/road/highway |

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
│   └── TaxiQueryService.java         # All spatial / temporal query logic
│
├── controller
│   └── TaxiController.java           # REST endpoints (WebFlux @RestController)
│
├── domain
│   └── record
│       ├── TaxiSnapshot.java         # R2DBC entity → taxi_snapshots
│       └── TaxiPosition.java         # R2DBC entity → taxi_positions
│
├── dto
│   ├── TaxiNearbyRequest.java
│   ├── TaxiNearbyResponse.java
│   ├── TaxiNearestResponse.java
│   ├── TaxiZoneCountResponse.java
│   ├── TaxiPolygonRequest.java
│   ├── TaxiRouteRequest.java
│   ├── TaxiHistoryResponse.java
│   ├── TaxiTimelineResponse.java
│   └── upstream
│       └── GovTaxiResponse.java      # Maps data.gov.sg GeoJSON response
│
├── repository
│   ├── TaxiSnapshotRepository.java   # ReactiveCrudRepository
│   └── TaxiPositionRepository.java   # Custom @Query with PostGIS functions
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
// Bulk insert with unnest
INSERT INTO taxi_positions (snapshot_id, longitude, latitude)
SELECT :snapshotId, unnest(:lons::float8[]), unnest(:lats::float8[])
```

### 6.4 Zone Data Seeding

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

| Scenario | HTTP Status | Exception class |
|----------|-------------|-----------------|
| Upstream API unavailable | 502 Bad Gateway | `UpstreamException` |
| Unknown zone name | 404 Not Found | `NotFoundException` |
| Invalid coordinates / radius ≤ 0 | 400 Bad Request | `ValidationException` |
| No snapshot available at `datetime` | 404 Not Found | `NotFoundException` |

All handled by the existing `GlobalExceptionHandler`.

---

## 9. Out of Scope (Future Work)

- **Real-time streaming** via WebSocket / SSE for live taxi position updates
- **Heatmap aggregation** endpoint (grid-based density)
- **Predictive availability** using historical time-series data
- **Authentication / rate limiting** on query endpoints
- **Caching layer** (Redis) for repeated identical spatial queries
