# Traffic Image Service - MVP Design Document

## Background

Traffic administrators and government agencies monitor Singapore's road network daily, but lack efficient tools to answer operational questions in real time:

> "Among all monitored cameras, **where is congestion worst** right now?"
>
> "Which cameras are showing **abnormal traffic flow**?"
>
> "Is traffic across the monitored area **operating normally**?"
>
> "**Show me all the cameras** on the map."
>
> "Give me a **summary of CTE traffic** — is it clear or congested along the whole corridor?"

Existing dashboards display camera feeds as a grid of images — operators must visually scan dozens of screens to identify problems. There is no automated triage, no cross-camera summarization (e.g., analyzing all 9 CTE cameras together to produce a single corridor-level assessment like "clear from Ang Mo Kio to Braddell, congested near Moulmein exit"), and no way to quickly answer "where should I focus attention right now?"

Singapore's government publishes real-time traffic camera images — ~87 cameras, refreshed every 20 seconds — but the raw data is just a list of image URLs and GPS coordinates. No context, no analysis, no way for an operator to get an at-a-glance situational overview.

**This service bridges that gap.** It ingests the camera feeds, lets administrators ask questions in natural language, and uses LLM vision (Claude) to analyze camera images across the network — surfacing congestion hotspots, flagging anomalies, and providing an overall traffic health assessment.

---

## 1. MVP Scope

### What MVP Does

1. **Ingest** — Poll the government API every 20s, store camera metadata and snapshots in PostgreSQL
2. **Analyze** — On each ingest cycle, call `civic-app POST /api/analyze-camera` for cameras with a new image (MD5 changed); store the structured analysis result in `camera_analysis`
3. **Serve** — Expose REST APIs to query cameras by expressway, location, or proximity; all responses include the latest pre-computed `analysis` object
4. **Display** — Operator dashboard with camera overview and LLM-driven traffic analysis (see `civic-frontend` → `docs/traffic-camera-ui-design.md`)

### Service Interaction

```
data.gov.sg API  (every 20s)
      │
      ▼
gov-data (Java — this project)
      │  on new image (md5 diff)
      ├─► POST civic-app /api/analyze-camera
      │         │
      │         └─► Ollama qwen3.5 (local vision)
      │                   │
      │         ◄──────────┘ { analysis }
      │
      ├─► UPSERT camera_snapshots
      ├─► UPSERT camera_analysis
      │
      ▼
  PostgreSQL
      │
      ▼
civic-frontend  ←  GET /api/cameras/...  (includes pre-computed analysis)
```

### What MVP Does NOT Do

These are deferred to future iterations:

| Feature | Why Deferred |
|---------|-------------|
| Anomaly detection & alerts | Requires background LLM monitoring — high token cost, complex |
| Historical replay & timelapse | Needs warm/cold storage tiers — adds operational complexity |
| Historical pattern analysis | Needs weeks of accumulated vision data to be meaningful |
| Multi-source fusion (camera + taxi) | Cross-service integration, can layer on later |
| WebSocket real-time push | Polling is fine for MVP |

---

## 2. Data Source

### API Endpoint

```
GET https://api.data.gov.sg/v1/transport/traffic-images?date_time={YYYY-MM-DDTHH:mm:ss}
```

### Response Structure (per camera)

```json
{
  "timestamp": "2026-03-17T11:56:21+08:00",
  "camera_id": "2708",
  "image": "https://images.data.gov.sg/api/traffic-images/...",
  "location": {
    "latitude": 1.3865,
    "longitude": 103.7747
  },
  "image_metadata": {
    "height": 1080,
    "width": 1920,
    "md5": "85f431ee693f22a5e401f3a81b5f6f87"
  }
}
```

### Key Data Characteristics

| Property | Value |
|----------|-------|
| Total cameras | ~87 |
| Refresh interval | Every 20 seconds |
| Resolutions | 1920x1080 (majority), 640x360, 320x240 |
| Location data | Latitude/Longitude per camera |
| Change detection | MD5 hash per image |

---

## 3. Database Schema

### `cameras` — Static camera registry

```sql
CREATE TABLE cameras (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       BIGINT NOT NULL UNIQUE,
    latitude        DECIMAL(12, 8) NOT NULL,
    longitude       DECIMAL(12, 8) NOT NULL,
    location_name   VARCHAR(255),          -- reverse-geocoded or from static mapping
    expressway      VARCHAR(50),           -- e.g., "BKE", "PIE", "CTE"
    resolution      VARCHAR(20),           -- "HD", "SD"
    first_seen_at   TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ
);
```

**Expressway population strategy:** The ~87 cameras are fixed — the government API always returns the same set. Use a static mapping table (camera_id → expressway) seeded on first deployment, based on camera_id prefix patterns and GPS coordinate clustering.

### `camera_snapshots` — Latest image data per camera

For MVP, we only keep the **latest snapshot per camera** (hot tier only). No historical retention.

```sql
CREATE TABLE camera_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       BIGINT NOT NULL REFERENCES cameras(camera_id),
    timestamp       TIMESTAMPTZ NOT NULL,
    image_url       TEXT NOT NULL,
    image_md5       VARCHAR(32) NOT NULL,
    image_width     INT,
    image_height    INT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE (camera_id, timestamp)
);

CREATE INDEX idx_snapshots_camera_time ON camera_snapshots (camera_id, timestamp DESC);
```

> **MVP simplification:** Each ingestion cycle UPSERTs (replaces) the latest snapshot per camera. No warm/cold tiers, no retention management, no purge jobs. The table stays at ~87 rows.

### `camera_analysis` — Latest LLM vision analysis per camera

Stores the most recent structured analysis result produced by calling the `civic-app` vision endpoint. One row per camera, replaced on each successful analysis.

```sql
CREATE TABLE camera_analysis (
    id               BIGSERIAL PRIMARY KEY,
    camera_id        BIGINT NOT NULL REFERENCES cameras(camera_id) UNIQUE,
    snapshot_id      BIGINT NOT NULL REFERENCES camera_snapshots(id),
    congestion       VARCHAR(20) NOT NULL,   -- free_flow | light | moderate | heavy | standstill
    vehicle_density  VARCHAR(20) NOT NULL,   -- empty | sparse | normal | dense | packed
    incidents        VARCHAR(20) NOT NULL,   -- none | accident | breakdown | obstruction | roadworks
    weather          VARCHAR(20) NOT NULL,   -- clear | rain | heavy_rain | fog
    road_surface     VARCHAR(20) NOT NULL,   -- dry | wet | flooded | construction
    summary          TEXT NOT NULL,
    analyzed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_camera ON camera_analysis (camera_id);
```

> **UNIQUE on `camera_id`** — ensures one row per camera. Each ingest cycle UPSERTs (replaces) the analysis when a new image is detected. The table stays at ~87 rows.

---

## 4. Scheduler

MVP has **one scheduled job**: snapshot ingestion + analysis.

### `ingest-snapshots` (every 20 seconds)

```
1. GET /transport/traffic-images?date_time={now}
   - Retry up to 3x with exponential backoff (1s, 2s, 4s)
   - If API down → log warning, skip cycle

2. For each camera in response:
   a. UPSERT into cameras table (update last_seen_at, coordinates)
   b. Compare incoming image_md5 against the stored snapshot md5
   c. If md5 is UNCHANGED → skip (image has not changed, no analysis needed)
   d. If md5 is NEW or camera is first seen:
      i.  UPSERT into camera_snapshots (replace previous snapshot)
      ii. POST /api/analyze-camera to civic-app with the image URL + camera_id + location_name
            - Download image from gov.sg URL, send as multipart/form-data
            - Retry up to 2x on timeout or 5xx
            - If civic-app is unreachable → log warning, skip analysis for this camera
      iii. On success: UPSERT into camera_analysis (replace previous analysis row)

3. Log metrics: cameras_processed, cameras_analyzed, api_latency_ms, analysis_latency_ms, errors
```

### Analysis Flow Detail

```
gov-data scheduler
    │
    │  (new image detected via md5 diff)
    │
    ├─► Download image bytes from images.data.gov.sg
    │
    ├─► POST civic-app /api/analyze-camera
    │       multipart fields:
    │         image        = <image bytes>
    │         camera_id    = "2701"
    │         location_name = "BKE - Woodlands"
    │
    └─► On 200: parse { analysis: { congestion, vehicle_density, ... } }
                UPSERT camera_analysis WHERE camera_id = ?
```

### Implementation

```java
@Component
@EnableScheduling
public class TrafficDataScheduler {

    @Scheduled(fixedRate = 20_000)  // every 20 seconds
    public void ingestSnapshots() { ... }
}
```

**Failure handling:**
- Gov API timeout → retry 3x, then skip cycle
- Partial response → ingest what's available, log warning
- DB write failure for individual camera → continue with others
- Snapshot overlap → `ON CONFLICT (camera_id, timestamp) DO NOTHING`
- civic-app timeout / 5xx → log warning, retain previous analysis row, continue with other cameras
- civic-app returns malformed JSON → log error, skip UPSERT for that camera

> **Why MD5 gating?** Images refresh every 20s but often stay identical. Calling vision analysis on unchanged images wastes local GPU resources and adds latency. Only analyzing new images keeps the analysis fresh and the scheduler fast.

---

## 5. API Design

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cameras` | List all cameras with latest snapshot |
| GET | `/api/cameras/{id}` | Single camera detail + latest image (`{id}` = camera_id, e.g., `2701`, `4702`) |
| GET | `/api/cameras/nearby?lat=&lng=&radius=` | Cameras within radius of a point (`lat`/`lng` = GPS coordinates, `radius` = meters) |
| GET | `/api/cameras/expressway/{code}` | All cameras along an expressway (`{code}` = expressway code, e.g., `BKE`, `PIE`, `CTE` — see Section 6) |
| GET | `/api/cameras/search?q=` | Search by location name (`q` = search keyword, e.g., `Woodlands`, `Tampines`) |

### Response Definitions

### `CameraDetail` Object

All camera list endpoints return a shared `CameraDetail` object. The `analysis` field is included when a vision analysis has been stored for that camera; it is `null` if the camera has never been successfully analyzed yet.

```json
{
  "cameraId": 2701,
  "locationName": "BKE - Woodlands",
  "latitude": 1.4470,
  "longitude": 103.7717,
  "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
  "timestamp": "2026-03-17T11:56:21+08:00",
  "resolution": "1920x1080",
  "analysis": {
    "congestion": "light",
    "vehicleDensity": "sparse",
    "incidents": "none",
    "weather": "clear",
    "roadSurface": "dry",
    "summary": "Light traffic moving freely near Woodlands checkpoint.",
    "analyzedAt": "2026-03-17T11:56:35+08:00"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `cameraId` | `number` | Unique camera identifier (e.g., `2701`) |
| `locationName` | `string?` | Human-readable location name, null if not mapped |
| `latitude` | `number` | GPS latitude |
| `longitude` | `number` | GPS longitude |
| `latestImage` | `string` | URL of the latest snapshot image |
| `timestamp` | `string` | ISO 8601 timestamp of the snapshot |
| `resolution` | `string` | Image resolution (e.g., `1920x1080`) |
| `analysis` | `Analysis?` | Latest LLM vision analysis; `null` if not yet analyzed |
| `analysis.congestion` | `string` | `free_flow` \| `light` \| `moderate` \| `heavy` \| `standstill` |
| `analysis.vehicleDensity` | `string` | `empty` \| `sparse` \| `normal` \| `dense` \| `packed` |
| `analysis.incidents` | `string` | `none` \| `accident` \| `breakdown` \| `obstruction` \| `roadworks` |
| `analysis.weather` | `string` | `clear` \| `rain` \| `heavy_rain` \| `fog` |
| `analysis.roadSurface` | `string` | `dry` \| `wet` \| `flooded` \| `construction` |
| `analysis.summary` | `string` | One-sentence human-readable description |
| `analysis.analyzedAt` | `string` | ISO 8601 timestamp of when the analysis was produced |

---

#### GET `/api/cameras`

Returns all cameras with their latest snapshot and analysis.

```json
{
  "success": true,
  "data": {
    "cameras": [
      {
        "cameraId": 2701,
        "locationName": "BKE - Woodlands",
        "latitude": 1.4470,
        "longitude": 103.7717,
        "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
        "timestamp": "2026-03-17T11:56:21+08:00",
        "resolution": "1920x1080",
        "analysis": { "congestion": "light", "vehicleDensity": "sparse", "incidents": "none", "weather": "clear", "roadSurface": "dry", "summary": "Light traffic near Woodlands.", "analyzedAt": "2026-03-17T11:56:35+08:00" }
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | `true` on success |
| `data.cameras` | `CameraDetail[]` | Array of all cameras — see `CameraDetail` schema above |

---

#### GET `/api/cameras/{id}`

Returns a single camera's detail with its latest image and analysis.

```json
{
  "success": true,
  "data": {
    "cameraId": 2701,
    "locationName": "BKE - Woodlands",
    "latitude": 1.4470,
    "longitude": 103.7717,
    "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
    "timestamp": "2026-03-17T11:56:21+08:00",
    "resolution": "1920x1080",
    "analysis": { "congestion": "light", "vehicleDensity": "sparse", "incidents": "none", "weather": "clear", "roadSurface": "dry", "summary": "Light traffic near Woodlands.", "analyzedAt": "2026-03-17T11:56:35+08:00" }
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | `true` on success |
| `data` | `CameraDetail` | Camera detail — see `CameraDetail` schema above |

**Error:** Returns `404` if camera_id does not exist.

---

#### GET `/api/cameras/nearby?lat=&lng=&radius=`

Returns cameras within the specified radius of a GPS point.

```json
{
  "success": true,
  "data": {
    "lat": 1.3521,
    "lng": 103.8198,
    "radius": 3000,
    "cameras": [
      {
        "cameraId": 1701,
        "locationName": "CTE - Moulmein",
        "latitude": 1.3553,
        "longitude": 103.8400,
        "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
        "timestamp": "2026-03-17T11:56:21+08:00",
        "resolution": "1920x1080",
        "analysis": { "congestion": "moderate", "vehicleDensity": "normal", "incidents": "none", "weather": "clear", "roadSurface": "dry", "summary": "Moderate traffic on CTE near Moulmein.", "analyzedAt": "2026-03-17T11:56:35+08:00" }
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | `true` on success |
| `data.lat` | `number` | The queried latitude |
| `data.lng` | `number` | The queried longitude |
| `data.radius` | `number` | The queried radius in meters |
| `data.cameras` | `CameraDetail[]` | Cameras within the radius, sorted by distance (nearest first) |

---

#### GET `/api/cameras/expressway/{code}`

Returns all cameras along an expressway.

```json
{
  "success": true,
  "data": {
    "expressway": "BKE",
    "name": "Bukit Timah Expressway",
    "camerasOnline": 7,
    "camerasTotal": 8,
    "cameras": [
      {
        "cameraId": 2701,
        "locationName": "BKE - Woodlands",
        "latitude": 1.4470,
        "longitude": 103.7717,
        "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
        "timestamp": "2026-03-17T11:56:21+08:00",
        "resolution": "1920x1080",
        "analysis": { "congestion": "light", "vehicleDensity": "sparse", "incidents": "none", "weather": "clear", "roadSurface": "dry", "summary": "Light traffic moving freely.", "analyzedAt": "2026-03-17T11:56:35+08:00" }
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | `true` on success |
| `data.expressway` | `string` | Expressway code (e.g., `BKE`) |
| `data.name` | `string` | Full expressway name |
| `data.camerasOnline` | `integer` | Number of cameras with a recent snapshot |
| `data.camerasTotal` | `integer` | Total cameras mapped to this expressway |
| `data.cameras` | `CameraDetail[]` | Array of cameras along the expressway |

**Error:** Returns `404` if expressway code is not recognized.

---

#### GET `/api/cameras/search?q=`

Returns cameras matching a location name search.

```json
{
  "success": true,
  "data": {
    "query": "Woodlands",
    "cameras": [
      {
        "cameraId": 2701,
        "locationName": "BKE - Woodlands",
        "latitude": 1.4470,
        "longitude": 103.7717,
        "latestImage": "https://images.data.gov.sg/api/traffic-images/...",
        "timestamp": "2026-03-17T11:56:21+08:00",
        "resolution": "1920x1080",
        "analysis": { "congestion": "light", "vehicleDensity": "sparse", "incidents": "none", "weather": "clear", "roadSurface": "dry", "summary": "Light traffic near Woodlands.", "analyzedAt": "2026-03-17T11:56:35+08:00" }
      }
    ]
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `success` | `boolean` | `true` on success |
| `data.query` | `string` | The search keyword used |
| `data.cameras` | `CameraDetail[]` | Cameras with location names matching the query (case-insensitive partial match) |

---

## 6. Expressway-Camera Mapping

> **Note:** Initial estimate based on camera_id prefix patterns and GPS clustering. Should be verified via reverse geocoding before production.

| Expressway | Code | Camera IDs | Count |
|------------|------|-----------|:-----:|
| Bukit Timah Expressway | BKE | 2701, 2702, 2703, 2704, 2705, 2706, 2707, 2708 | 8 |
| Pan Island Expressway | PIE | 4701, 4702, 4704, 4705, 4706, 4707, 4708, 4709, 4710, 4712, 4713, 4714, 4716 | 13 |
| Central Expressway | CTE | 1701, 1702, 1703, 1704, 1705, 1706, 1707, 1709, 1711 | 9 |
| East Coast Parkway | ECP | 1001, 1002, 1003, 1004, 1501, 1502, 1503, 1504, 1505 | 9 |
| Tampines Expressway | TPE | 7791, 7793, 7794, 7795, 7796, 7797, 7798 | 7 |
| Seletar Expressway | SLE | 9701, 9702, 9703, 9704, 9705, 9706 | 6 |
| Kranji Expressway | KJE | 8701, 8702, 8704, 8706 | 4 |
| Ayer Rajah Expressway | AYE | 1801, 1802, 4798, 4799 | 4 |
| Kallang-Paya Lebar Expy | KPE | 3793, 3795, 3796, 3797, 3798, 5794, 5795, 5797, 5798, 5799 | 10 |

---

## 7. Frontend UI Design

See `civic-frontend` → `docs/traffic-camera-ui-design.md` for detailed wireframes and component design.

**MVP views:**
- Network overview map (all cameras on Mapbox with status indicators)
- Corridor view (cameras along an expressway with LLM traffic summary)
- Camera detail (single camera image + LLM situational analysis)

---

## 8. Non-Goals
- Background LLM monitoring (deferred — MVP is on-demand only)

---

## 9. Future Enhancements

Features to add after MVP is validated:

| Feature | What It Adds | Prerequisite |
|---------|-------------|-------------|
| **Historical replay** | Store 7 days of snapshots, timelapse playback | Warm storage tier, purge scheduler |
| **Anomaly alerts** | Detect offline/frozen cameras, push alerts | `camera_alerts` table, WebSocket, background scheduler |
| **Operator watchlists** | Save camera groups, scheduled summary reports | User accounts, `operator_watchlists` table |
| **Historical patterns** | "Is PIE consistently congested on Friday evenings?" | Weeks of accumulated vision data, `historical_patterns` table |
| **Camera + taxi fusion** | Combine traffic cameras with taxi availability | Cross-service API integration |
| **Proactive LLM monitoring** | Background vision analysis, auto-alerts | Higher token budget, rate limiting |
