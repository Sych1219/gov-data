# Traffic Image Service - MVP Design Document

## Background

Every day, Singapore commuters ask questions that no existing app can answer:

> "May I know if the Causeway queue looks long — but **how bad is it really**? Is it backed up to the highway?"
>
> "I heard there's an accident on PIE — but **has it been cleared yet**? Should I still avoid it?"
>
> "It's pouring rain — **is there flooding** on my route through Tampines?"
>
> "CTE is red on the map — but **is traffic completely stuck, or just slow**?"

Google Maps and Waze show colored lines and icons. They tell you *that* there's congestion, but not *what it actually looks like*. They can't show you the scene, interpret what's happening, or give you a judgment call.

Singapore's government publishes real-time traffic camera images — ~87 cameras, refreshed every 20 seconds — but the raw data is just a list of image URLs and GPS coordinates. No context, no analysis, no way for a commuter to quickly check "is my route okay?"

**This service bridges that gap.** It ingests the camera feeds, lets users ask questions in natural language, and uses LLM vision (Claude) to actually *read* the camera images and answer: "CTE is jammed from Braddell to AMK — take PIE instead."

---

## 1. MVP Scope

### What MVP Does

1. **Ingest** — Poll the government API every 20s, store camera metadata and snapshots in PostgreSQL
2. **Serve** — Expose REST APIs to query cameras by expressway, location, or proximity
3. **Analyze** — On user query, use LLM vision to read camera images and return a natural language assessment
4. **Display** — Chat-driven frontend shows camera images and LLM analysis (see `civic-frontend` → `docs/traffic-camera-ui-design.md`)

### What MVP Does NOT Do

These are deferred to future iterations:

| Feature | Why Deferred |
|---------|-------------|
| Anomaly detection & alerts | Requires background LLM monitoring — high token cost, complex |
| Historical replay & timelapse | Needs warm/cold storage tiers — adds operational complexity |
| Personalized commute profiles | Needs user accounts, saved routes, scheduled briefings |
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
    camera_id       VARCHAR(10) PRIMARY KEY,
    latitude        DECIMAL(12, 8) NOT NULL,
    longitude       DECIMAL(12, 8) NOT NULL,
    location_name   VARCHAR(255),          -- reverse-geocoded or from static mapping
    expressway      VARCHAR(50),           -- e.g., "BKE", "PIE", "CTE"
    resolution      VARCHAR(20),           -- "HD", "SD"
    first_seen_at   TIMESTAMP,
    last_seen_at    TIMESTAMP
);
```

**Expressway population strategy:** The ~87 cameras are fixed — the government API always returns the same set. Use a static mapping table (camera_id → expressway) seeded on first deployment, based on camera_id prefix patterns and GPS coordinate clustering.

### `camera_snapshots` — Latest image data per camera

For MVP, we only keep the **latest snapshot per camera** (hot tier only). No historical retention.

```sql
CREATE TABLE camera_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       VARCHAR(10) NOT NULL REFERENCES cameras(camera_id),
    timestamp       TIMESTAMP NOT NULL,
    image_url       TEXT NOT NULL,
    image_md5       VARCHAR(32) NOT NULL,
    image_width     INT,
    image_height    INT,
    created_at      TIMESTAMP DEFAULT NOW(),

    UNIQUE (camera_id, timestamp)
);

CREATE INDEX idx_snapshots_camera_time ON camera_snapshots (camera_id, timestamp DESC);
```

> **MVP simplification:** Each ingestion cycle UPSERTs (replaces) the latest snapshot per camera. No warm/cold tiers, no retention management, no purge jobs. The table stays at ~87 rows.

---

## 4. Scheduler

MVP has **one scheduled job**: snapshot ingestion.

### `ingest-snapshots` (every 20 seconds)

```
1. GET /transport/traffic-images?date_time={now}
   - Retry up to 3x with exponential backoff (1s, 2s, 4s)
   - If API down → log warning, skip cycle

2. For each camera in response:
   a. UPSERT into cameras table (update last_seen_at, coordinates)
   b. UPSERT into camera_snapshots (replace previous snapshot for this camera)

3. Log metrics: cameras_processed, api_latency_ms, errors
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
- API timeout → retry 3x, then skip cycle
- Partial response → ingest what's available, log warning
- DB write failure for individual camera → continue with others
- Overlap → `ON CONFLICT (camera_id, timestamp) DO NOTHING`

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

### Example Response: GET `/api/cameras/expressway/BKE`

```json
{
  "expressway": "BKE",
  "name": "Bukit Timah Expressway",
  "cameras_online": 7,
  "cameras_total": 8,
  "cameras": [
    {
      "camera_id": "2701",
      "location_name": "BKE - Woodlands",
      "latitude": 1.4470,
      "longitude": 103.7717,
      "latest_image": "https://...",
      "timestamp": "2026-03-17T11:56:21+08:00",
      "resolution": "1920x1080"
    }
  ]
}
```

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
- Camera map (all cameras on Mapbox)
- Corridor view (cameras along an expressway with LLM summary)
- Camera detail (single camera image + LLM analysis)

---

## 8. Non-Goals

- Route planning or navigation (use Google Maps)
- ETA calculation (use Google Maps)
- Background LLM monitoring (deferred — MVP is on-demand only)

---

## 9. Future Enhancements

Features to add after MVP is validated:

| Feature | What It Adds | Prerequisite |
|---------|-------------|-------------|
| **Historical replay** | Store 7 days of snapshots, timelapse playback | Warm storage tier, purge scheduler |
| **Anomaly alerts** | Detect offline/frozen cameras, push alerts | `camera_alerts` table, WebSocket, background scheduler |
| **Commute profiles** | Save routes, daily departure briefings | User accounts, `commute_profiles` table |
| **Historical patterns** | "Is PIE always bad on Fridays?" | Weeks of accumulated vision data, `historical_patterns` table |
| **Camera + taxi fusion** | Combine traffic cameras with taxi availability | Cross-service API integration |
| **Proactive LLM monitoring** | Background vision analysis, auto-alerts | Higher token budget, rate limiting |
