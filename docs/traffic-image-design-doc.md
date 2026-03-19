# Traffic Image Service - Design Document

## Background

Every day, Singapore commuters ask questions that no existing app can answer:

> "The Causeway queue looks long on Google Maps — but **how bad is it really**? Is it backed up to the highway?"
>
> "Google says there's an accident on PIE — but **has it been cleared yet**? Should I still avoid it?"
>
> "It's pouring rain — **is there flooding** on my route through Tampines?"
>
> "CTE is red on the map — but **is traffic completely stuck, or just slow**?"
>
> "I commute Woodlands to CBD every morning — **can something just tell me when to leave**?"

Google Maps and Waze show colored lines and icons. They tell you *that* there's congestion, but not *what it actually looks like*. They can't show you the scene, interpret what's happening, or give you a judgment call.

Singapore's government publishes real-time traffic camera images — ~87 cameras, refreshed every 20 seconds — but the raw data is just a list of image URLs and GPS coordinates. No context, no analysis, no way for a commuter to quickly check "is my route okay?"

**This service bridges that gap.** It ingests the camera feeds, lets users ask questions in natural language, and uses LLM vision (Claude) to actually *read* the camera images and answer: "CTE is jammed from Braddell to AMK — take PIE instead."

The rest of this document describes how:

| Section | What It Covers |
|---------|---------------|
| [1. Overview](#1-overview) | What this service is and isn't |
| [2. Target Users](#2-target-users) | Who we're building for |
| [3. Problem Statement](#3-problem-statement) | The specific gaps in existing tools |
| [4. Data Source](#4-data-source) | The government API we consume |
| [5. User Questions & Features](#5-user-questions--features) | What users can ask, and what the system does |
| [6. Frontend UI Design](#6-frontend-ui-design) | Chat-driven split-panel interface |
| [7. Data Storage & Scheduler](#7-data-storage-design) | Schema, ingestion pipeline, scheduled jobs |
| [8. API Design](#8-api-design-backend) | Backend endpoints |
| [9. Expressway-Camera Mapping](#9-expressway-camera-mapping) | How cameras map to roads |
| [10. Chat Integration](#10-chat-integration) | LLM agent query flow |
| [11. Non-Goals](#11-non-goals) | What we explicitly don't do |
| [12. LLM Intelligence](#12-llm-intelligence-features) | Vision analysis, anomaly detection, commute assistant |
| [13. Implementation Details](#13-llm-implementation-details) | Model selection, cost, latency targets |

---

## 1. Overview

This service consumes Singapore's Traffic Image API (`/transport/traffic-images`) and provides real-time traffic camera views to the public. The API returns ~87 camera snapshots every 20 seconds, each with image URL, GPS coordinates, timestamp, and image metadata.

### Core Value Proposition

> Google Maps tells you "there's a jam", but traffic cameras let you **see why it's jammed and how bad it really is**.

This service is **not** a replacement for Google Maps. It is a complement — focused on two things Google Maps cannot do: **showing real-time camera footage** and **using LLM vision intelligence to interpret what the cameras see**.

### LLM Intelligence Layer

Beyond simply displaying camera images, this service uses LLM vision models (Claude) to:
- **Analyze** camera images to assess congestion, detect incidents, and describe road conditions
- **Synthesize** multiple camera feeds along a corridor into actionable travel advice
- **Monitor** feeds continuously and proactively alert users to anomalies
- **Personalize** insights based on individual commute patterns
- **Correlate** traffic camera data with taxi availability data for richer situational awareness

---

## 2. Target Users

### Primary: Singapore Residents & Commuters

Citizens who want to **visually verify** road conditions before or during their commute.

### User Characteristics

| Attribute | Detail |
|-----------|--------|
| Device | Mobile-first (80%+), desktop secondary |
| Usage pattern | Quick glance before leaving, typically < 2 minutes |
| Technical level | Non-technical, expects simple UX |
| Language | English (primary), with local place names (Tampines, Jurong, CTE, PIE) |

---

## 3. Problem Statement

Google Maps and Waze already solve route planning, ETA, and congestion visualization. However, they **cannot** answer:

| User Question | Why Google Maps Can't Answer |
|---------------|------------------------------|
| "Let me **see** the Woodlands Causeway queue — how long is it?" | Google only shows red/orange lines, not actual queue length |
| "PIE has an accident — **is it serious? Has it been cleared?**" | Google marks "accident" but can't show the scene |
| "It's raining heavily — **is there flooding** near Tampines?" | Google has no road surface visibility data |
| "CTE says congested — **is it completely stuck or still moving?**" | Red line doesn't distinguish between slow-moving and standstill |
| "Construction zone — **how many lanes are actually closed?**" | Google shows "construction" icon but no visual detail |

### One-line Summary

Users want to **see with their own eyes** what's actually happening on the road.

---

## 4. Data Source

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

### Data Volume Estimates

| Period | Records per camera | Total (~87 cameras) |
|--------|:--:|:--:|
| 1 hour | 180 | 15,660 |
| 1 day | 4,320 | 375,840 |
| 1 month | ~129,600 | ~11.3 million |

---

## 5. User Questions & Features

### 5.1 Core Feature: "Let Me See"

The primary interaction is: user names a location or road → system shows the relevant camera feeds.

| User Question | System Behavior |
|---------------|-----------------|
| "Show me Woodlands Causeway" | Find cameras near Woodlands checkpoint coordinates, display live images |
| "What does PIE look like right now?" | Find cameras along PIE corridor, display as ordered strip |
| "Show cameras near me" | Use browser geolocation, find nearest cameras by lat/lng |
| "Is there flooding near Tampines?" | Show cameras in Tampines area so user can visually check |

### 5.2 Hot Spots (Always Visible)

Certain locations have consistently high demand and should be surfaced by default:

| Hot Spot | Why |
|----------|-----|
| Woodlands Causeway | Daily Malaysia-Singapore commuters checking queue length |
| Tuas Checkpoint | Same as above, second crossing |
| CTE (towards city) | Morning rush hour bottleneck |
| PIE (key interchanges) | Highest-traffic expressway |
| ECP (towards Changi) | Airport-bound travelers |

### 5.3 Anomaly Alerts

When the system detects unusual patterns, surface them proactively:

| Detection Method | What It Means | How to Show |
|------------------|---------------|-------------|
| Camera timestamp > 40s stale | Camera offline | Grey out on map with "Offline" label |
| Consecutive MD5 identical (>5 min) | Frozen image or standstill traffic | Yellow warning badge |
| Multiple nearby cameras go stale | Possible area-wide issue | Alert banner: "Possible incident near [area]" |

### 5.4 Historical View

| User Question | System Behavior |
|---------------|-----------------|
| "What did CTE look like 30 minutes ago?" | Replay stored frames (20s intervals) as a filmstrip or animation |
| "Is Monday 8am usually bad on PIE?" | Show historical snapshots from same time/day across past weeks |

---

## 6. Frontend UI Design

### 6.1 Design Principles

1. **Chat-driven** — All interactions start from natural language queries in the left panel
2. **Split-panel layout** — Same pattern as the taxi dashboard: chat left, visual content right
3. **Location-driven, not ID-driven** — Users say "Woodlands", not "camera 2701"
4. **Context-adaptive right panel** — Right panel content changes based on what the user asks
5. **Complement, don't compete** — No route planning, no ETA, no navigation

---

## 7. Data Storage Design

### 7.1 Storage Strategy

**Hybrid approach:**

| Tier | What to Store | Retention | Purpose |
|------|--------------|-----------|---------|
| Hot | Latest snapshot per camera (metadata + image URL) | Always current | Real-time display |
| Warm | All snapshots with MD5 changes | 7 days | Historical replay, anomaly detection |
| Cold | Sampled snapshots (1 per 5 min) | 90 days | Trend analysis, historical comparison |

### 7.2 Database Schema

#### `cameras` — Static camera registry

```sql
CREATE TABLE cameras (
    camera_id       VARCHAR(10) PRIMARY KEY,
    latitude        DECIMAL(12, 8) NOT NULL,
    longitude       DECIMAL(12, 8) NOT NULL,
    location_name   VARCHAR(255),          -- reverse-geocoded name
    expressway      VARCHAR(50),           -- e.g., "BKE", "PIE", "CTE"
    resolution      VARCHAR(20),           -- "HD", "SD"
    first_seen_at   TIMESTAMP,
    last_seen_at    TIMESTAMP
);
```

#### `camera_snapshots` — Time-series image data

```sql
CREATE TABLE camera_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       VARCHAR(10) NOT NULL REFERENCES cameras(camera_id),
    timestamp       TIMESTAMP NOT NULL,
    image_url       TEXT NOT NULL,
    image_md5       VARCHAR(32) NOT NULL,
    image_width     INT,
    image_height    INT,
    md5_changed     BOOLEAN DEFAULT TRUE,  -- false if same as previous frame
    created_at      TIMESTAMP DEFAULT NOW(),

    UNIQUE (camera_id, timestamp)
);

-- Index for time-range queries per camera
CREATE INDEX idx_snapshots_camera_time ON camera_snapshots (camera_id, timestamp DESC);

-- Index for finding stale cameras
CREATE INDEX idx_snapshots_timestamp ON camera_snapshots (timestamp DESC);
```

#### `camera_alerts` — Detected anomalies

```sql
CREATE TABLE camera_alerts (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       VARCHAR(10) NOT NULL REFERENCES cameras(camera_id),
    alert_type      VARCHAR(50) NOT NULL,  -- 'OFFLINE', 'FROZEN', 'RESOLUTION_CHANGE'
    started_at      TIMESTAMP NOT NULL,
    resolved_at     TIMESTAMP,
    details         JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

#### `vision_analyses` — LLM vision analysis results (cached)

```sql
CREATE TABLE vision_analyses (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       VARCHAR(10) NOT NULL REFERENCES cameras(camera_id),
    snapshot_id     BIGINT NOT NULL REFERENCES camera_snapshots(id),
    model           VARCHAR(50) NOT NULL,      -- e.g., "claude-sonnet-4-6"
    congestion_level VARCHAR(20),              -- 'free_flow', 'light', 'moderate', 'heavy', 'standstill'
    vehicle_density  VARCHAR(20),              -- 'empty', 'sparse', 'normal', 'dense', 'packed'
    incidents       JSONB,                     -- [{"type": "accident", "description": "..."}]
    weather         VARCHAR(50),               -- 'clear', 'rain', 'heavy_rain', 'fog'
    road_condition  VARCHAR(50),               -- 'dry', 'wet', 'flooded', 'construction'
    summary         TEXT NOT NULL,             -- Human-readable description
    confidence      DECIMAL(3,2),              -- 0.00 - 1.00
    analysis_time_ms INT,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_vision_camera_time ON vision_analyses (camera_id, created_at DESC);
```

#### `commute_profiles` — User saved commute routes

```sql
CREATE TABLE commute_profiles (
    id              BIGSERIAL PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL,     -- anonymous session or authenticated user
    profile_name    VARCHAR(100),              -- e.g., "Morning commute"
    origin_name     VARCHAR(255),              -- e.g., "Woodlands"
    origin_lat      DECIMAL(12, 8),
    origin_lng      DECIMAL(12, 8),
    dest_name       VARCHAR(255),              -- e.g., "CBD"
    dest_lat        DECIMAL(12, 8),
    dest_lng        DECIMAL(12, 8),
    route_cameras   VARCHAR(10)[] NOT NULL,    -- ordered list of camera_ids along route
    expressways     VARCHAR(50)[],             -- e.g., ["SLE", "CTE"]
    departure_times JSONB,                     -- {"mon": "08:00", "tue": "08:00", ...}
    active          BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_commute_user ON commute_profiles (user_id);
```

#### `historical_patterns` — Pre-aggregated congestion patterns

```sql
CREATE TABLE historical_patterns (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       VARCHAR(10) NOT NULL REFERENCES cameras(camera_id),
    day_of_week     SMALLINT NOT NULL,         -- 0=Monday, 6=Sunday
    hour            SMALLINT NOT NULL,         -- 0-23
    avg_congestion  VARCHAR(20),               -- average congestion_level from vision_analyses
    sample_count    INT NOT NULL,
    last_updated    TIMESTAMP DEFAULT NOW(),

    UNIQUE (camera_id, day_of_week, hour)
);
```

### 7.3 Data Ingestion Flow

```
Every 20 seconds:
  1. Call API → get all cameras
  2. For each camera:
     a. Upsert into `cameras` table (update last_seen_at, coordinates)
     b. Compare MD5 with latest stored snapshot
     c. If MD5 changed → INSERT into `camera_snapshots` (md5_changed = true)
     d. If MD5 same → INSERT with md5_changed = false (or skip based on tier)
     e. Check alert conditions:
        - timestamp stale > 40s → create OFFLINE alert
        - MD5 unchanged > 5 min → create FROZEN alert
  3. Clean up: archive warm → cold tier (daily job)
```

---

## 7.4 Scheduler & Pre-Processing Pipeline

The system requires multiple scheduled tasks at different intervals to keep data fresh, detect anomalies, manage storage tiers, and pre-compute analytics.

### Scheduler Overview

| Job | Interval | Priority | Description |
|-----|----------|----------|-------------|
| `ingest-snapshots` | Every 20s | CRITICAL | Poll API, ingest camera snapshots into DB |
| `detect-anomalies` | Every 60s | HIGH | Rule-based pre-filter for alerts (offline, frozen, incident) |
| `vision-monitor` | Every 60s | MEDIUM | LLM vision analysis on anomaly candidates |
| `warm-to-cold` | Daily 02:00 SGT | LOW | Downsample warm tier → cold tier (1 per 5 min) |
| `aggregate-patterns` | Daily 02:30 SGT | LOW | Aggregate vision results into `historical_patterns` |
| `purge-expired` | Daily 03:00 SGT | LOW | Delete warm tier data older than 7 days, cold tier > 90 days |
| `camera-health-check` | Every 5 min | MEDIUM | Update camera registry, detect new/removed cameras |

### 7.4.1 Snapshot Ingestion Job (`ingest-snapshots`)

This is the core scheduler — runs every 20 seconds and must be highly reliable.

```
┌─────────────────────────────────────────────────────────┐
│  ingest-snapshots (every 20s)                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. GET /transport/traffic-images?date_time={now}       │
│     ├── Retry up to 3x with exponential backoff        │
│     └── If API down → log + skip cycle (do NOT crash)  │
│                                                         │
│  2. For each camera in response (parallel, batch=20):   │
│     ├── UPSERT cameras table                           │
│     │   ├── Update last_seen_at                        │
│     │   └── Update coordinates if changed              │
│     │                                                   │
│     ├── Fetch latest stored snapshot for camera         │
│     │                                                   │
│     ├── Compare image_md5:                             │
│     │   ├── Changed → INSERT snapshot (md5_changed=T)  │
│     │   └── Same → INSERT snapshot (md5_changed=F)     │
│     │         (warm tier may skip unchanged frames)     │
│     │                                                   │
│     └── Emit event to anomaly detection queue           │
│                                                         │
│  3. Record ingestion metrics:                           │
│     ├── cameras_processed, snapshots_inserted           │
│     ├── md5_changed_count, api_latency_ms               │
│     └── errors (per camera + total)                     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Failure handling:**
- API timeout → retry 3x (backoff: 1s, 2s, 4s), then skip cycle
- Partial response (fewer cameras than expected) → ingest what's available, log warning
- DB write failure for individual camera → continue with remaining cameras, retry failed ones next cycle
- Scheduler drift → use fixed-rate scheduling (not fixed-delay), tolerate overlap by using `ON CONFLICT DO NOTHING` on the unique constraint `(camera_id, timestamp)`

### 7.4.2 Anomaly Detection Job (`detect-anomalies`)

Runs every 60 seconds. Uses rule-based heuristics as a cheap pre-filter before invoking LLM vision.

```
┌─────────────────────────────────────────────────────────┐
│  detect-anomalies (every 60s)                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. Query cameras with latest snapshot:                 │
│     SELECT c.camera_id, s.timestamp, s.image_md5,      │
│            s.md5_changed                                │
│     FROM cameras c                                      │
│     JOIN camera_snapshots s ON ...                      │
│     WHERE s.timestamp = (latest per camera)             │
│                                                         │
│  2. Apply rules:                                        │
│     ├── OFFLINE: NOW() - s.timestamp > 40s             │
│     ├── FROZEN: consecutive md5_changed=false > 5 min  │
│     │   (count snapshots with same md5 in last 5 min)  │
│     ├── INCIDENT: md5 change rate > 3 in last 60s      │
│     │   (sudden visual changes = something happened)   │
│     └── AREA_INCIDENT: ≥3 adjacent cameras flagged     │
│                                                         │
│  3. For each candidate:                                 │
│     ├── Check if alert already exists (avoid dupes)    │
│     ├── Queue for LLM vision verification              │
│     └── Insert preliminary alert (unverified)          │
│                                                         │
│  4. Resolve stale alerts:                               │
│     ├── OFFLINE camera now responding → resolve alert  │
│     └── FROZEN camera md5 changed → resolve alert      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 7.4.3 Storage Tier Management (`warm-to-cold`)

Runs daily at 02:00 SGT during low-traffic hours.

```
warm-to-cold (daily 02:00 SGT):

  1. Identify warm-tier snapshots older than 7 days

  2. Downsample: keep 1 snapshot per 5 minutes per camera
     - Prefer frames where md5_changed = true
     - Among candidates in each 5-min window, keep the one
       closest to the window midpoint

  3. Delete non-selected snapshots (batch delete, 1000 rows/tx)

  4. Vacuum/analyze affected tables

purge-expired (daily 03:00 SGT):

  1. Delete cold-tier snapshots older than 90 days
  2. Delete orphaned vision_analyses (snapshot_id no longer exists)
  3. Delete resolved alerts older than 30 days
```

### 7.4.4 Pattern Aggregation (`aggregate-patterns`)

Runs daily at 02:30 SGT. Pre-computes the `historical_patterns` table.

```
aggregate-patterns (daily 02:30 SGT):

  1. For each camera:
     SELECT camera_id,
            EXTRACT(DOW FROM created_at) as day_of_week,
            EXTRACT(HOUR FROM created_at) as hour,
            MODE() WITHIN GROUP (ORDER BY congestion_level) as avg_congestion,
            COUNT(*) as sample_count
     FROM vision_analyses
     WHERE created_at > NOW() - INTERVAL '30 days'
     GROUP BY camera_id, day_of_week, hour

  2. UPSERT into historical_patterns
     (update avg_congestion and sample_count, set last_updated = NOW())
```

### 7.4.5 Implementation: Spring Boot `@Scheduled`

Since the backend is a Spring Boot application, use `@Scheduled` for in-process jobs and consider external scheduling for heavier batch work.

```java
@Component
@EnableScheduling
public class TrafficDataScheduler {

    @Scheduled(fixedRate = 20_000)  // every 20 seconds
    public void ingestSnapshots() { ... }

    @Scheduled(fixedRate = 60_000)  // every 60 seconds
    public void detectAnomalies() { ... }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cameraHealthCheck() { ... }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Singapore")
    public void warmToColdMigration() { ... }

    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Singapore")
    public void aggregatePatterns() { ... }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Singapore")
    public void purgeExpired() { ... }
}
```

**Scaling considerations:**
- For single-instance deployment: `@Scheduled` is sufficient
- For multi-instance deployment: use distributed locking (e.g., ShedLock with PostgreSQL) to ensure only one instance runs each job
- The 20s ingestion job is the most latency-sensitive — if it takes >15s, consider splitting cameras across worker threads using `@Async`

### 7.4.6 Pre-Processing Pipeline

Before data is available for API queries, the ingestion job applies these pre-processing steps:

| Step | When | Purpose |
|------|------|---------|
| MD5 dedup | On ingest | Compare with previous snapshot, set `md5_changed` flag |
| Camera registry update | On ingest | Detect new cameras, update coordinates, mark `last_seen_at` |
| Reverse geocoding | On new camera detected | Map lat/lng → human-readable `location_name` and `expressway` |
| Image URL validation | On ingest | HEAD request to verify image URL is accessible (async, non-blocking) |
| Anomaly event emission | On ingest | Push camera state to in-memory queue for anomaly detection |
**Reverse geocoding** (for new cameras only):
```
New camera detected (camera_id not in cameras table):
  1. Call Mapbox/Google Reverse Geocoding API with (lat, lng)
  2. Extract road name, area name
  3. Match against known expressway corridors (within 500m of polyline)
  4. Populate location_name and expressway fields
```

### 7.4.7 Vision Analysis Strategy

Vision analysis is **on-demand only** — triggered by user queries, not by background jobs. This keeps token costs low and simple to reason about.

```
User query ("Is CTE jammed?")
  → Resolve cameras [1701, 1702, ..., 1711]
  → For each camera:
      1. Fetch latest snapshot from camera_snapshots
      2. Check vision_analyses: cached result with same image_md5?
         - Yes → return cached result (no LLM call)
         - No  → call LLM vision (Haiku/Sonnet) → INSERT into vision_analyses
  → Aggregate results → respond to user
```

**Cache hit condition:** If the image hasn't changed (same `image_md5`), reuse the existing analysis. No wasted LLM calls on identical frames.

**Estimated cost (~500 user queries/day, avg 5 cameras each):**

| | Per call | Per day |
|--|:--------:|:-------:|
| Input tokens | ~1,500 (image + prompt) | ~3.75M |
| Output tokens | ~300 (JSON response) | ~750K |
| Cost (Haiku) | ~$0.002 | **~$5** |

---

## 8. API Design (Backend)

### 8.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cameras` | List all cameras with latest snapshot |
| GET | `/api/cameras/{id}` | Single camera detail + latest image |
| GET | `/api/cameras/{id}/history?from=&to=` | Historical snapshots for a camera |
| GET | `/api/cameras/nearby?lat=&lng=&radius=` | Cameras within radius of a point |
| GET | `/api/cameras/expressway/{code}` | All cameras along an expressway (e.g., BKE, PIE) |
| GET | `/api/cameras/search?q=` | Search by location name |
| GET | `/api/alerts/active` | Currently active anomaly alerts |
| POST | `/api/chat` | Natural language query → LLM-powered structured response |
| POST | `/api/analyze/camera/{id}` | Vision analysis of a single camera's current image |
| POST | `/api/analyze/corridor/{code}` | Vision analysis of all cameras along an expressway |
| POST | `/api/analyze/compare` | Compare two time points for a camera (change detection) |
| GET | `/api/analysis-cache/{camera_id}` | Cached latest vision analysis result |
| GET | `/api/commute/profile` | Get user's saved commute profile |
| PUT | `/api/commute/profile` | Save/update user's commute profile |
| GET | `/api/commute/briefing` | Get personalized commute briefing based on profile |
| GET | `/api/history/pattern?camera_id=&day=&hour=` | Aggregated historical pattern data |
| POST | `/api/replay` | Generate timelapse replay with LLM narration |
| POST | `/api/fusion/query` | Multi-source query (cameras + taxi data) |

### 8.2 Example Response: GET `/api/cameras/expressway/BKE`

```json
{
  "expressway": "BKE",
  "name": "Bukit Timah Expressway",
  "direction": "North to South",
  "cameras_online": 7,
  "cameras_total": 7,
  "cameras": [
    {
      "camera_id": "2701",
      "location_name": "BKE - Woodlands",
      "latitude": 1.4470,
      "longitude": 103.7717,
      "latest_image": "https://...",
      "timestamp": "2026-03-17T11:56:21+08:00",
      "status": "online",
      "resolution": "1920x1080"
    }
  ]
}
```

---

## 9. Expressway-Camera Mapping

Cameras are assigned to expressways based on GPS proximity to known expressway corridors.

> **Note:** The mapping below is an **initial estimate** based on camera_id prefix patterns and GPS coordinate clustering. It has NOT been verified via reverse geocoding. Before production use, each camera's lat/lng should be validated against actual expressway polylines (e.g., via Mapbox/Google Reverse Geocoding API) to confirm it is within 500m of the road corridor.

Initial mapping for key expressways:

| Expressway | Code | Camera IDs | Count |
|------------|------|-----------|:-----:|
| Bukit Timah Expressway | BKE | 2701, 2702, 2703, 2704, 2705, 2706, 2707, 2708 | 8 |
| Pan Island Expressway | PIE | 4701, 4702, 4704, 4705, 4706, 4707, 4708, 4709, 4710, 4712, 4713, 4714, 4716 | 13 |
| Central Expressway | CTE | 1701, 1702, 1703, 1704, 1705, 1706, 1707, 1709, 1711 | 9 |
| East Coast Parkway | ECP | 1001, 1002, 1003, 1004, 1501, 1502, 1503, 1504, 1505 | 9 |
| Tampines Expressway | TPE | 7791, 7793, 7794, 7795, 7796, 7797, 7798 | 7 |
| Seletar Expressway | SLE | 9701, 9702, 9703, 9704, 9705, 9706 | 6 |
| Kranji Expressway | KJE | 8701, 8702, 8704, 8706 | 4 |
| Ayer Rajah Expressway | AYE | 4798, 4799, 1704, 1707 | 4 |
| Kallang-Paya Lebar Expy | KPE | 3793, 3795, 3796, 3797, 3798, 5794, 5795, 5797, 5798, 5799 | 10 |

---

## 10. Chat Integration

The ChatPanel accepts natural language queries. Unlike a simple keyword-to-API mapper, the LLM acts as an **intelligent agent** that decides which APIs to call, analyzes camera images via vision, and synthesizes results into natural language answers.

### 10.1 Query Flow

```
User query
  → LLM (intent classification + planning)
  → Tool calls (one or more API calls, executed in parallel where possible)
  → LLM Vision (analyze returned camera images)
  → LLM (synthesize findings into natural language response)
  → Response to user (text + relevant images)
```

### 10.2 Supported Query Types

| User Says | Intent | Backend Behavior |
|-----------|--------|-----------------|
| "Show me Woodlands" | Location search | `GET /api/cameras/search?q=Woodlands` → return images |
| "BKE cameras" | Expressway view | `GET /api/cameras/expressway/BKE` → return corridor |
| "Cameras near me" | Nearby search | `GET /api/cameras/nearby?lat=...&lng=...` |
| "Is CTE jammed?" | **Intelligent analysis** | Fetch CTE cameras → LLM vision analyzes each image → synthesize corridor summary |
| "Is CTE jammed from Braddell to AMK?" | **Segment analysis** | Fetch CTE cameras in segment → vision analysis → congestion assessment + alternative route suggestion |
| "What happened on PIE 30 min ago?" | Historical view | `GET /api/cameras/{id}/history?from=...&to=...` → vision comparison |
| "Any accidents right now?" | Active alerts | `GET /api/alerts/active` + vision verification |
| "Is PIE always this bad on Friday evenings?" | **Pattern analysis** | Fetch historical data for same day/time → LLM summarizes trends |
| "Show me Woodlands at 8am this morning" | **Replay** | Fetch historical frames → generate timelapse + LLM narration |
| "Easy to get a taxi near Orchard?" | **Multi-source fusion** | Fetch cameras + taxi availability → combined assessment |
| "How long from the accident to traffic recovery?" | **Event analysis** | LLM scans historical frames to identify event boundaries |

---

## 11. Non-Goals

Things this service explicitly does **not** do:

- Route planning or navigation (use Google Maps)
- ETA calculation (use Google Maps)
- Congestion color-coded map overlay (use Google Maps)
- ~~AI-based vehicle counting or accident detection~~ → **Moved to in-scope** (see Section 12: LLM Intelligence)
- Real-time video streaming (API provides snapshots, not video)

---

## 12. LLM Intelligence Features

This section describes the six LLM-powered capabilities that transform the service from a simple camera viewer into an intelligent traffic assistant.

### 12.1 Intelligent Traffic Analysis

> **Core idea:** User asks "Is CTE jammed?", system doesn't just show images — it **reads** the images and tells you what it sees.

#### How It Works

```
User: "Is CTE jammed?"
  │
  ▼
1. Resolve "CTE" → camera IDs [1701, 1702, 1703, ..., 1711]
  │
  ▼
2. Fetch latest snapshot for each camera (parallel)
  │
  ▼
3. Send images to LLM Vision (Claude) with structured prompt:
   "Analyze this traffic camera image. Assess:
    - Congestion level (free_flow / light / moderate / heavy / standstill)
    - Visible incidents (accident, breakdown, construction)
    - Weather conditions (rain, fog, clear)
    - Lane utilization (how many lanes occupied)
    - Vehicle movement (flowing, slow, stopped)"
  │
  ▼
4. Aggregate per-camera results into corridor summary:
   - Segment-by-segment breakdown (North→South)
   - Overall corridor assessment
   - Alternative route suggestions if congested
  │
  ▼
5. Return to user:
   "CTE is moderately congested overall. Braddell to Ang Mo Kio segment is
    severe (nearly standstill). North of Seletar is clear. Suggest taking
    PIE to SLE as an alternative route."
   + Annotated camera images for key segments
```

#### Vision Analysis Prompt Template

```
You are a Singapore traffic analyst. Analyze this traffic camera image.

Camera: {camera_id} — {location_name}
Expressway: {expressway_code}
Time: {timestamp}

Provide a structured assessment:
1. **Congestion**: free_flow | light | moderate | heavy | standstill
2. **Vehicle density**: empty | sparse | normal | dense | packed
3. **Incidents**: Any visible accident, breakdown, or obstruction? Describe if yes.
4. **Weather**: clear | rain | heavy_rain | fog
5. **Road surface**: dry | wet | flooded | construction
6. **Summary**: One sentence describing what you see.

Respond in JSON format.
```

#### Caching Strategy

- Cache vision analysis results in `vision_analyses` table
- TTL: same as camera refresh interval (20 seconds)
- Only re-analyze when `image_md5` changes (no point analyzing identical frames)
- For corridor queries, analyze all cameras in parallel to minimize latency

#### Cost Management

| Scenario | Images per Query | Estimated Cost |
|----------|:---:|:---:|
| Single camera analysis | 1 | ~$0.002 |
| Expressway corridor (CTE, 9 cameras) | 9 | ~$0.018 |
| Full island scan (87 cameras) | 87 | ~$0.17 |

- **Optimization**: Skip re-analysis if MD5 unchanged since last analysis
- **Batching**: For continuous monitoring, batch multiple cameras per API call where possible
- **Model selection**: Use Claude Haiku for routine monitoring, Sonnet/Opus for user-facing queries requiring high accuracy

---

### 12.2 Proactive Anomaly Detection

> **Core idea:** Don't wait for users to ask. LLM continuously monitors feeds and pushes alerts when something unusual happens.

#### Detection Pipeline

```
Every 60 seconds (configurable):
  │
  ▼
1. Rule-based pre-filter (cheap, fast):
   - MD5 unchanged > 5 min → candidate: FROZEN
   - Timestamp stale > 40s → candidate: OFFLINE
   - MD5 change rate spike (> 3 changes in 60s) → candidate: INCIDENT
   - Multiple adjacent cameras flagged → candidate: AREA_INCIDENT
  │
  ▼
2. LLM Vision verification (only for candidates):
   - Fetch current + previous frame
   - Ask LLM to compare and describe change:
     "Compare these two frames from the same camera, 10 minutes apart.
      Is there a significant change? Describe what happened."
  │
  ▼
3. Alert classification:
   - LLM confirms or dismisses the anomaly
   - If confirmed → generate human-readable alert description
   - Assign severity: INFO / WARNING / CRITICAL
  │
  ▼
4. Push to users:
   - Update `camera_alerts` table
   - Broadcast via WebSocket to connected clients
   - Show as alert card in UI
```

#### Alert Types

| Type | Trigger | LLM Role | Example Output |
|------|---------|----------|---------------|
| OFFLINE | Timestamp stale > 40s | Verify last frame is valid | "Camera 1705 (CTE near Braddell) offline since 14:32" |
| FROZEN | MD5 unchanged > 5 min | Compare with nearby cameras to rule out actual standstill | "Camera 4709 (PIE Clementi) frozen — nearby cameras show normal flow, likely malfunction" |
| INCIDENT | Sudden MD5 change pattern | Analyze before/after frames | "PIE towards Toa Payoh: traffic went from normal to near-standstill within 10 minutes, possible accident" |
| CONGESTION_SPIKE | Vision detects sudden congestion increase | Compare with historical baseline | "CTE Ang Mo Kio segment: sudden congestion spike, significantly worse than historical average for this time" |
| WEATHER | Vision detects rain/fog/flood | Analyze image for weather | "Multiple cameras along ECP detected heavy rain with waterlogged road surface" |

#### Distinguishing Real Congestion from Camera Issues

A frozen MD5 could mean:
- Camera malfunction (frozen image) → **alert**
- Actual traffic standstill (nothing moving) → **congestion alert, not camera issue**

LLM distinguishes by checking adjacent cameras:
- If nearby cameras also show standstill → likely real congestion
- If nearby cameras show normal flow → likely frozen camera

---

### 12.3 Personalized Commute Assistant

> **Core idea:** "I drive Woodlands to CBD every morning." System learns your route, watches the cameras along it, and gives you a departure briefing.

#### User Onboarding Flow

```
User: "I commute from Woodlands to CBD via SLE and CTE"
  │
  ▼
1. LLM extracts route info:
   - Origin: Woodlands (1.4370, 103.7860)
   - Destination: CBD (1.2830, 103.8513)
   - Route: SLE → CTE
  │
  ▼
2. System maps to cameras:
   - SLE cameras: [9701, 9702, 9703, 9704, 9705, 9706]
   - CTE cameras: [1701, 1702, 1703, 1704, 1705, 1706, 1707, 1709, 1711]
  │
  ▼
3. Save commute profile:
   - Route cameras (ordered by distance along route)
   - Usual departure time (learned or specified)
  │
  ▼
4. Confirm: "Saved! I'll check all 15 cameras along SLE + CTE every morning and give you a departure recommendation."
```

#### Daily Briefing Generation

```
At [departure_time - 15 min]:
  │
  ▼
1. Fetch all route cameras' latest images
  │
  ▼
2. Run vision analysis on each (parallel)
  │
  ▼
3. Compare with historical patterns for same day/time:
   - Query `historical_patterns` for baseline
   - Flag segments that are worse than usual
  │
  ▼
4. Generate briefing:
   "🟢 SLE is clear. CTE Seletar to Braddell has light congestion, slightly
    better than usual. Recommend departing at your normal time. Road score: 7/10."
   OR
   "🔴 CTE Ang Mo Kio segment has severe congestion (much worse than usual).
    Recommend leaving 15 minutes later or taking PIE instead."
```

#### Adaptive Learning

- Track actual departure patterns (when user opens the app)
- Learn day-of-week variations (e.g., WFH on Wednesdays)
- Adjust briefing timing based on usage

---

### 12.4 Historical Pattern Analysis

> **Core idea:** Use accumulated warm/cold tier data to answer questions about traffic patterns over time.

#### Capabilities

| Query Type | Example | Data Source | LLM Role |
|-----------|---------|-------------|----------|
| Weekly pattern | "Is PIE always this bad on Friday evenings?" | `historical_patterns` table | Summarize trend, compare to other days |
| Event lookup | "When was the last time Woodlands queue spilled onto the road?" | `vision_analyses` + snapshot history | Search for matching descriptions |
| Trend analysis | "Has CTE gotten worse this month?" | Time-series of congestion levels | Statistical trend + natural language summary |
| Comparison | "How does today compare to last Friday?" | Two time-point analyses | Side-by-side vision comparison |

#### Historical Pattern Aggregation Job

```
Daily at 02:00 SGT:
  │
  ▼
1. For each camera:
   - Group vision_analyses by (day_of_week, hour)
   - Calculate mode of congestion_level
   - Count samples
  │
  ▼
2. Upsert into `historical_patterns` table
  │
  ▼
3. This enables fast lookups like:
   "What's the typical congestion at camera 1705 on Friday at 18:00?"
   → Direct DB query, no LLM needed for baseline
   → LLM only needed for natural language synthesis
```

#### Visual Similarity Search (Future Enhancement)

- Generate image embeddings (e.g., CLIP) for each snapshot
- Store in vector database
- Enable queries like: "Find past images that look like this" (drag-and-drop a screenshot)
- Useful for incident pattern matching

---

### 12.5 Multi-Source Data Fusion

> **Core idea:** Combine traffic camera data with taxi availability data to give richer, more actionable answers.

#### Architecture

```
User: "Is it easy to get a taxi near Orchard?"
  │
  ▼
LLM Agent (orchestrator)
  ├── Tool 1: GET /api/cameras/nearby?lat=1.3048&lng=103.8318&radius=1000
  │   → Camera images around Orchard
  │
  ├── Tool 2: POST /api/v1/query (taxi service)
  │   → body: {"query": "taxi availability near Orchard"}
  │   → Returns taxi count + heatmap data
  │
  ▼
LLM Vision: Analyze camera images for traffic conditions
  │
  ▼
LLM Synthesis:
  "Orchard Road is moderately congested, with about 23 available taxis
   nearby. Suggest waiting at ION Orchard entrance or the Wheelock Place
   side — less traffic there, easier to board."
```

#### Fusion Query Types

| Query | Camera Data | Taxi Data | Combined Insight |
|-------|-------------|-----------|-----------------|
| "Easy to get a taxi near Orchard?" | Road congestion level | Available taxis nearby | Taxi availability + best pickup spots based on traffic flow |
| "Construction here, where did the taxis go?" | Construction visible in camera | Taxi distribution shift | Explain why taxis avoid the area, suggest alternatives |
| "Easy to get a taxi in CBD when it rains?" | Weather detection from cameras | Historical taxi data in rain | Rain impact on taxi availability + wait time estimate |
| "ECP is jammed, any taxis at Changi?" | ECP congestion from cameras | Taxi supply at Changi | Whether airport taxi queue is building up |

#### Cross-Service Data Contract

The fusion layer requires both backends to be accessible:

```
Traffic Camera Backend: {cameraBackendUrl}/api/...
Taxi Service Backend:   {taxiBackendUrl}/api/v1/...
```

The LLM agent has both as available tools and decides which to call based on query intent.

---

### 12.6 Natural Language Replay

> **Core idea:** Instead of a raw timelapse, LLM narrates what happened over a time period.

#### Replay Flow

```
User: "Show me what Woodlands looked like at 8am this morning"
  │
  ▼
1. Resolve time range: 2026-03-18T08:00 to 08:30 SGT (default 30 min window)
  │
  ▼
2. Fetch historical frames:
   GET /api/cameras/2701/history?from=2026-03-18T00:00:00Z&to=2026-03-18T00:30:00Z
   → Returns ~90 frames (20s intervals) with MD5 dedup → ~30 unique frames
  │
  ▼
3. Sample key frames (reduce cost):
   - Select frames where MD5 changed (skip static periods)
   - Cap at ~10 frames for analysis
  │
  ▼
4. LLM Vision analyzes each sampled frame in sequence:
   "Frame 1 (08:00): Light traffic, clear weather, all lanes open.
    Frame 4 (08:06): Traffic building up, left two lanes slowing.
    Frame 7 (08:14): Heavy congestion, vehicles nearly stopped.
    Frame 10 (08:28): Traffic clearing, flow resuming."
  │
  ▼
5. Generate narrative + timelapse:
   - Frontend stitches frames into animated playback
   - LLM provides text overlay / narration timeline
   - Key moments highlighted (congestion start, peak, clearance)
```

#### Event Boundary Detection

For queries like "How long from the accident until traffic recovered?":

```
1. Start from the alert timestamp (or user-specified time)
2. Scan forward in 5-min increments:
   - Analyze each frame for incident presence
   - Detect "clearance" — when traffic resumes normal flow
3. Report:
   "Incident occurred at 14:23 (Camera 4709), 2 lanes blocked.
    14:52 tow truck arrived, 15:18 scene cleared, 15:35 traffic flow resumed.
    Total time from incident to recovery: approximately 1 hour 12 minutes."
```

---

## 13. LLM Implementation Details

### 13.1 Model Selection Strategy

| Use Case | Model | Reasoning |
|----------|-------|-----------|
| Real-time user queries | Claude Sonnet | Balance of speed and accuracy for interactive use |
| Detailed corridor analysis | Claude Opus | Highest accuracy for multi-image synthesis |
| Continuous monitoring (background) | Claude Haiku | Cost-efficient for high-volume routine checks |
| Historical pattern summarization | Claude Sonnet | Good enough for aggregated data analysis |

### 13.2 Vision API Call Pattern

```python
# Pseudo-code for single camera analysis
response = claude.messages.create(
    model="claude-sonnet-4-6",
    max_tokens=500,
    messages=[{
        "role": "user",
        "content": [
            {
                "type": "image",
                "source": {
                    "type": "url",
                    "url": snapshot.image_url
                }
            },
            {
                "type": "text",
                "text": TRAFFIC_ANALYSIS_PROMPT.format(
                    camera_id=camera.camera_id,
                    location_name=camera.location_name,
                    expressway=camera.expressway,
                    timestamp=snapshot.timestamp
                )
            }
        ]
    }]
)
```

### 13.3 Agent Tool Definitions

The LLM chat agent has access to the following tools:

```json
[
  {
    "name": "search_cameras",
    "description": "Search cameras by location name or expressway code",
    "parameters": { "query": "string" }
  },
  {
    "name": "get_camera_image",
    "description": "Get the latest image from a specific camera",
    "parameters": { "camera_id": "string" }
  },
  {
    "name": "get_corridor_images",
    "description": "Get all camera images along an expressway",
    "parameters": { "expressway_code": "string" }
  },
  {
    "name": "get_camera_history",
    "description": "Get historical snapshots for a camera within a time range",
    "parameters": { "camera_id": "string", "from": "ISO8601", "to": "ISO8601" }
  },
  {
    "name": "get_active_alerts",
    "description": "Get currently active anomaly alerts",
    "parameters": {}
  },
  {
    "name": "query_taxi_availability",
    "description": "Query real-time taxi availability near a location",
    "parameters": { "location": "string", "lat": "number", "lng": "number", "radius_m": "number" }
  },
  {
    "name": "get_historical_pattern",
    "description": "Get average congestion pattern for a camera by day and hour",
    "parameters": { "camera_id": "string", "day_of_week": "number", "hour": "number" }
  },
  {
    "name": "analyze_image",
    "description": "Run LLM vision analysis on a camera image and return structured assessment",
    "parameters": { "camera_id": "string", "snapshot_id": "number" }
  },
  {
    "name": "get_commute_profile",
    "description": "Get user's saved commute route and preferences",
    "parameters": { "user_id": "string" }
  }
]
```

### 13.4 Cost Estimation

| Feature | Frequency | Images/call | Daily Cost (est.) |
|---------|-----------|:-----------:|:-----------------:|
| User queries | ~500/day | 1-10 | ~$2-5 |
| Continuous monitoring | Every 60s, ~10 candidates | 2-5 | ~$15-30 |
| Commute briefings | ~200 users × 1/day | 10-15 | ~$3-6 |
| Historical analysis | ~50/day | 5-10 | ~$1-3 |
| **Total estimated** | | | **~$20-45/day** |

Optimization levers:
- MD5-based dedup (skip unchanged frames) — biggest saver
- Haiku for background monitoring — 10x cheaper than Sonnet
- Cache analysis results aggressively (20s TTL per camera)
- Batch prompts where possible (multiple cameras in one call)

### 13.5 Latency Targets

| Feature | Target | Strategy |
|---------|--------|----------|
| Single camera analysis | < 3s | Direct Sonnet call |
| Corridor analysis (9 cameras) | < 5s | Parallel vision calls |
| Commute briefing | < 8s | Pre-fetched images + parallel analysis |
| Historical replay | < 10s | Sampled frames (max 10) + parallel |
| Chat response (simple) | < 2s | Cached results where possible |
