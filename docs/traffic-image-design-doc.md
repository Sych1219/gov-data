# Traffic Image Service - Design Document

## 1. Overview

This service consumes Singapore's Traffic Image API (`/transport/traffic-images`) and provides real-time traffic camera views to the public. The API returns ~87 camera snapshots every 20 seconds, each with image URL, GPS coordinates, timestamp, and image metadata.

### Core Value Proposition

> Google Maps tells you "there's a jam", but traffic cameras let you **see why it's jammed and how bad it really is**.

This service is **not** a replacement for Google Maps. It is a complement — focused on one thing Google Maps cannot do: **showing real-time camera footage**.

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

1. **Mobile-first** — Most users check on their phone before leaving
2. **One-tap to see** — Minimal steps between opening the app and seeing camera footage
3. **Location-driven, not ID-driven** — Users say "Woodlands", not "camera 2701"
4. **Complement, don't compete** — No route planning, no ETA, no navigation

### 6.2 Page Layout (Mobile)

```
┌──────────────────────────┐
│  🔍 Search: "Woodlands"   │  ← Search bar (top, always visible)
│                          │
│  📍 Hot Spots             │
│  ┌──────┐ ┌──────┐      │  ← Horizontal scroll, top picks
│  │Wood- │ │Tuas  │      │
│  │lands │ │Check │      │
│  │[img] │ │[img] │      │
│  └──────┘ └──────┘      │
│                          │
│  🔴 Alerts                │  ← Only shown when anomalies detected
│  ┌──────────────────┐   │
│  │ PIE near Toa      │   │
│  │ Payoh - possible   │   │
│  │ incident           │   │
│  │ [Tap to view]      │   │
│  └──────────────────┘   │
│                          │
│  🗺️ Map                  │  ← Mapbox with camera markers
│  ┌──────────────────┐   │
│  │    📷  📷         │   │
│  │  📷      📷      │   │
│  │      📷    📷     │   │
│  └──────────────────┘   │
│                          │
│  💬 Ask                   │  ← Chat input
│  "Is CTE still jammed?"  │
└──────────────────────────┘
```

### 6.3 Camera Detail View

When user taps a camera or search result:

```
┌──────────────────────────┐
│  ← Back                  │
│                          │
│  PIE - near Toa Payoh    │  ← Human-readable location name
│  Camera 1005             │  ← Secondary info
│  Last updated: 11:56 SGT │
│                          │
│  ┌──────────────────┐   │
│  │                  │   │
│  │   [Live Image]   │   │  ← Full-width camera image
│  │   1920 x 1080    │   │
│  │                  │   │
│  └──────────────────┘   │
│                          │
│  ◀ 11:55  11:56  ▶      │  ← Timeline scrubber (20s steps)
│                          │
│  Nearby cameras:         │
│  ┌──────┐ ┌──────┐      │  ← Other cameras within 2km
│  │ 1006 │ │ 1003 │      │
│  └──────┘ └──────┘      │
└──────────────────────────┘
```

### 6.4 Expressway Corridor View

When user searches for an expressway (e.g., "BKE"):

```
┌──────────────────────────┐
│  ← Back                  │
│                          │
│  BKE - Bukit Timah Expy  │
│  7 cameras | All online  │
│                          │
│  North → South           │
│  ┌──────┐ ┌──────┐      │
│  │ 2701 │ │ 2702 │      │
│  │Woodl.│ │      │      │
│  └──────┘ └──────┘      │
│  ┌──────┐ ┌──────┐      │
│  │ 2704 │ │ 2706 │      │
│  └──────┘ └──────┘      │
│  ┌──────┐ ┌──────┐      │
│  │ 2707 │ │ 2708 │      │
│  └──────┘ └──────┘      │
│  ┌──────┐               │
│  │ 2705 │               │
│  └──────┘               │
│                          │
│  [🗺️ Show on map]        │
└──────────────────────────┘
```

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
| POST | `/api/chat` | Natural language query → structured response |

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

The ChatPanel accepts natural language queries and maps them to API calls:

| User Says | Intent | API Call |
|-----------|--------|----------|
| "Show me Woodlands" | Location search | `GET /api/cameras/search?q=Woodlands` |
| "BKE cameras" | Expressway view | `GET /api/cameras/expressway/BKE` |
| "Cameras near me" | Nearby search | `GET /api/cameras/nearby?lat=...&lng=...` |
| "Is CTE jammed?" | Expressway status | `GET /api/cameras/expressway/CTE` + anomaly check |
| "What happened on PIE 30 min ago?" | Historical view | `GET /api/cameras/{id}/history?from=...&to=...` |
| "Any accidents right now?" | Active alerts | `GET /api/alerts/active` |

---

## 11. Non-Goals

Things this service explicitly does **not** do:

- Route planning or navigation (use Google Maps)
- ETA calculation (use Google Maps)
- Congestion color-coded map overlay (use Google Maps)
- AI-based vehicle counting or accident detection (future phase)
- Real-time video streaming (API provides snapshots, not video)
