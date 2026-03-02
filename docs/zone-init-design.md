# Zone Initialization Design Document

> **Context**: The `zones` table schema already exists (see `schema.sql`) but is empty.
> Zone-based API endpoints (`/zone/{zoneName}/count`, `/road/{roadName}/count`) silently
> return zero because the JOIN against `zones` finds no matching row.
> This document describes how to populate and maintain zone data.

---

## 1. Problem Statement

`TaxiPositionRepository.countInZone()` executes:

```sql
SELECT COUNT(*) AS cnt
FROM taxi_positions tp
JOIN zones z ON z.name = :zoneName
WHERE tp.snapshot_id = :snapshotId
  AND ST_Within(tp.geog::geometry, z.geog::geometry)
```

If no row exists in `zones` with a matching `name`, the JOIN returns zero rows and
the count is `0` — indistinguishable from "zone exists but has no taxis". The fix
requires both populating zone data **and** adding a zone-existence check so the API
can return `404` for unknown zone names.

---

## 2. Zone Data Model (existing schema)

```sql
CREATE TABLE IF NOT EXISTS zones (
    id       SERIAL    PRIMARY KEY,
    name     TEXT      NOT NULL UNIQUE,   -- query key, e.g. 'CBD', 'Tampines', 'PIE'
    category TEXT      NOT NULL,          -- 'district' | 'road' | 'highway'
    geog     GEOGRAPHY NOT NULL           -- Polygon for districts, LineString for roads
);

CREATE INDEX IF NOT EXISTS idx_zones_name ON zones (name);
CREATE INDEX IF NOT EXISTS idx_zones_geog ON zones USING GIST (geog);
```

**Key constraints**:
- `name` must be `UNIQUE` — the API takes it as a path parameter
- `category` drives which zones `countNearRoad` considers (`'road'` or `'highway'`)
- `geog` must be `GEOGRAPHY` (not `GEOMETRY`) to match `taxi_positions.geog`

---

## 3. Data Sources

### 3.1 District Polygons — URA/OneMap Planning Areas

Singapore's Urban Redevelopment Authority (URA) publishes planning area and subzone
boundaries via the OneMap API (Singapore Government's official geospatial platform).

| Resource | URL |
|----------|-----|
| OneMap Planning Areas | `https://www.onemap.gov.sg/api/public/geodata/PlanningAreasGeo` |
| OneMap Subzones | `https://www.onemap.gov.sg/api/public/geodata/SubzonesGeo` |
| URA GeoJSON (static) | `https://data.gov.sg/datasets?query=planning+area` |

Response is GeoJSON `FeatureCollection` where each feature's `properties.pln_area_n`
(or `subzone_n`) is the zone name and `geometry` is the polygon.

### 3.2 Road/Highway LineStrings — OpenStreetMap (Overpass API)

Major expressways and arterial roads can be fetched as LineStrings from OSM.

```
Overpass API: https://overpass-api.de/api/interpreter
```

Example Overpass QL query to fetch PIE in Singapore:
```
[out:json];
way["name"="Pan Island Expressway"]["highway"~"motorway"](1.2,103.6,1.5,104.0);
out geom;
```

### 3.3 Fallback — Static GeoJSON seed file

For a low-dependency, always-available baseline, include a hand-curated
`zones-seed.json` (checked into source control) with approximate geometries
for the most commonly queried zones. This ensures the service works even if
external APIs are unavailable at startup.

---

## 4. Canonical Zone Catalog

Minimum viable zone set, ordered by expected query frequency:

### 4.1 Districts (`category = 'district'`)

| `name` | Description |
|--------|-------------|
| `CBD` | Central Business District (Raffles Place, Tanjong Pagar, Marina Bay) |
| `Orchard` | Orchard Road shopping belt |
| `Marina Bay` | Marina Bay Sands, Gardens by the Bay area |
| `Changi` | Changi Airport and surroundings |
| `Tampines` | Tampines planning area (east) |
| `Jurong East` | Jurong East commercial hub |
| `Woodlands` | Woodlands (north, Causeway area) |
| `Bishan` | Bishan–Ang Mo Kio central area |
| `Punggol` | Punggol new town (northeast) |
| `Sentosa` | Sentosa Island resort area |
| `Harbourfront` | VivoCity, HarbourFront terminal |
| `Novena` | Novena medical / commercial district |
| `Toa Payoh` | Toa Payoh HDB estate |
| `Buona Vista` | One-North / Buona Vista research hub |

### 4.2 Roads (`category = 'road'`)

| `name` | Description |
|--------|-------------|
| `Orchard Road` | Main shopping arterial |
| `Shenton Way` | CBD financial corridor |
| `Beach Road` | Beach Road / Bugis |
| `Thomson Road` | North–south arterial |

### 4.3 Highways (`category = 'highway'`)

| `name` | Description |
|--------|-------------|
| `PIE` | Pan Island Expressway |
| `CTE` | Central Expressway |
| `ECP` | East Coast Parkway |
| `SLE` | Seletar Expressway |
| `BKE` | Bukit Timah Expressway |
| `KPE` | Kallang–Paya Lebar Expressway |
| `MCE` | Marina Coastal Expressway |
| `TPE` | Tampines Expressway |
| `AYE` | Ayer Rajah Expressway |

---

## 5. Implementation Strategy

### Two-Phase Approach

```
Application Startup
       │
       ▼
Phase 1 ── schema.sql (table creation, already done)
       │
       ▼
Phase 2 ── ZoneDataInitializer @Component
       │       ├─ Check: zones table empty?
       │       │   NO  → skip (idempotent)
       │       │   YES → load seed data
       │       │
       │       └─ Seed Source Priority:
       │           1. OneMap Planning Areas API  (online, authoritative)
       │           2. Bundled zones-seed.json    (offline fallback)
       │
       ▼
Service Ready
```

**Idempotency rule**: The initializer checks `SELECT COUNT(*) FROM zones` before
inserting. If count > 0, it does nothing. This prevents duplicate-key errors on
restart and lets operators manually manage zone data if needed.

---

## 6. Implementation Plan

### 6.1 New Files

```
src/main/
├── java/com/gov/app/
│   ├── config/
│   │   └── ZoneDataInitializer.java    ← Spring @Component, ApplicationRunner
│   ├── domain/
│   │   └── Zone.java                   ← R2DBC entity for zones table
│   ├── repository/
│   │   └── ZoneRepository.java         ← count(), existsByName(), batchInsert()
│   └── service/
│       └── ZoneSeedService.java        ← orchestrates fetch + insert
└── resources/
    └── zones-seed.json                 ← bundled fallback GeoJSON FeatureCollection
```

### 6.2 Modified Files

| File | Change |
|------|--------|
| `TaxiQueryService.java` | Add zone-existence check before `countInZone`; throw `NotFoundException` if absent |
| `application.yml` | Add `zone.onemap.enabled` toggle and `zone.onemap.url` config |

---

## 7. Detailed Component Design

### 7.1 `Zone.java` — R2DBC Entity

```java
@Table("zones")
@Data
public class Zone {
    @Id
    private Integer id;
    private String name;
    private String category;
    // geog is write-only via raw SQL; not mapped here (same pattern as TaxiPosition.geog)
}
```

### 7.2 `ZoneRepository.java`

```java
@Repository
public class ZoneRepository {

    private final DatabaseClient db;

    /** Returns true if any zone rows exist. */
    public Mono<Boolean> hasAnyZone() {
        return db.sql("SELECT COUNT(*) AS c FROM zones")
                .map(row -> row.get("c", Long.class) > 0)
                .one();
    }

    /** Returns true if a zone with the given name exists. */
    public Mono<Boolean> existsByName(String name) {
        return db.sql("SELECT 1 FROM zones WHERE name = :name LIMIT 1")
                .bind("name", name)
                .fetch().one()
                .map(r -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Inserts zones in bulk.
     * geogWkt is WKT, e.g. "POLYGON((...))" or "LINESTRING(...)".
     */
    public Flux<Long> batchInsert(List<ZoneSeedEntry> entries) {
        return Flux.fromIterable(entries)
                .flatMap(e -> db.sql("""
                        INSERT INTO zones (name, category, geog)
                        VALUES (:name, :category,
                                ST_GeogFromText(:wkt))
                        ON CONFLICT (name) DO NOTHING
                        """)
                        .bind("name", e.name())
                        .bind("category", e.category())
                        .bind("wkt", e.wkt())
                        .fetch().rowsUpdated());
    }
}
```

`ZoneSeedEntry` is a simple record:
```java
public record ZoneSeedEntry(String name, String category, String wkt) {}
```

### 7.3 `ZoneSeedService.java`

```java
@Service
@Slf4j
public class ZoneSeedService {

    private final ZoneRepository zoneRepository;
    private final WebClient webClient;
    private final boolean onemapEnabled;          // from config
    private final String onemapUrl;               // from config
    private final Resource seedFile;              // classpath:zones-seed.json

    /** Entry point called by ZoneDataInitializer. */
    public Mono<Void> seedIfEmpty() {
        return zoneRepository.hasAnyZone()
                .flatMap(hasData -> {
                    if (hasData) {
                        log.info("zones table already populated, skipping seed");
                        return Mono.empty();
                    }
                    return loadAndInsert();
                });
    }

    private Mono<Void> loadAndInsert() {
        Mono<List<ZoneSeedEntry>> source = onemapEnabled
                ? fetchFromOnemap().onErrorResume(ex -> {
                    log.warn("OneMap fetch failed ({}), falling back to bundled seed", ex.getMessage());
                    return loadFromFile();
                  })
                : loadFromFile();

        return source
                .flatMapMany(zoneRepository::batchInsert)
                .doOnNext(n -> log.debug("inserted {} zone row(s)", n))
                .then()
                .doOnSuccess(v -> log.info("zone seed complete"));
    }

    /** Fetch Singapore planning areas from OneMap and convert to WKT entries. */
    private Mono<List<ZoneSeedEntry>> fetchFromOnemap() { ... }

    /** Parse bundled zones-seed.json from classpath resources. */
    private Mono<List<ZoneSeedEntry>> loadFromFile() { ... }
}
```

### 7.4 `ZoneDataInitializer.java`

```java
@Component
@RequiredArgsConstructor
public class ZoneDataInitializer implements ApplicationRunner {

    private final ZoneSeedService zoneSeedService;

    @Override
    public void run(ApplicationArguments args) {
        // Block briefly on startup; this is acceptable for initialization
        zoneSeedService.seedIfEmpty()
                .doOnError(ex -> log.error("zone seed failed, zone queries may return 0", ex))
                .onErrorResume(ex -> Mono.empty())   // don't prevent app startup
                .block(Duration.ofSeconds(30));
    }
}
```

### 7.5 `zones-seed.json` — Bundled Fallback

Stored at `src/main/resources/zones-seed.json`. Format is a GeoJSON `FeatureCollection`
where each feature has `name` and `category` properties and a `Polygon` or `LineString`
geometry. Approximate polygons are sufficient for zone queries; they do not need to match
URA boundaries exactly.

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": { "name": "CBD", "category": "district" },
      "geometry": {
        "type": "Polygon",
        "coordinates": [[
          [103.8198, 1.2789],
          [103.8547, 1.2789],
          [103.8547, 1.2966],
          [103.8198, 1.2966],
          [103.8198, 1.2789]
        ]]
      }
    },
    {
      "type": "Feature",
      "properties": { "name": "PIE", "category": "highway" },
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [103.6780, 1.3400],
          [103.7200, 1.3350],
          [103.7700, 1.3300],
          [103.8200, 1.3200],
          [103.8700, 1.3100],
          [103.9200, 1.3050]
        ]
      }
    }
    // ... remaining zones
  ]
}
```

> Road/highway zones store a `LineString`. The existing `countNearRoad` query uses
> `ST_DWithin` (distance buffer), not `ST_Within`, so a LineString geometry is correct.
> District zones store a `Polygon` for use with `ST_Within`.

### 7.6 Fix Silent Zero in `TaxiQueryService.java`

Add a zone-existence check before running the spatial query:

```java
public Mono<TaxiZoneCountResponse> countInZone(String zoneName, String datetime) {
    return zoneRepository.existsByName(zoneName)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.error(new NotFoundException(
                            "Zone not found: '" + zoneName + "'. " +
                            "Check GET /api/v1/zones for available zone names."));
                }
                return resolveSnapshot(datetime)
                        .flatMap(snap -> positionRepo.countInZone(snap.getId(), zoneName))
                        .map(count -> new TaxiZoneCountResponse(zoneName, count, /* snapshotTime */));
            });
}
```

This surfaces a `404 Not Found` when the zone name is invalid, making the API
unambiguous (zero taxis vs. unknown zone).

### 7.7 Optional: `GET /api/v1/zones` listing endpoint

A lightweight endpoint to let callers discover valid zone names:

```
GET /api/v1/zones?category=district        → list district names
GET /api/v1/zones?category=highway         → list highway names
GET /api/v1/zones                          → all zones
```

Response:
```json
{
  "zones": [
    { "name": "CBD",      "category": "district" },
    { "name": "Tampines", "category": "district" },
    { "name": "PIE",      "category": "highway"  }
  ]
}
```

---

## 8. `application.yml` Additions

```yaml
zone:
  seed:
    onemap:
      enabled: true
      planning-areas-url: https://www.onemap.gov.sg/api/public/geodata/PlanningAreasGeo
    fallback-file: classpath:zones-seed.json
```

---

## 9. Geometry Source Decision Matrix

| Scenario | Recommended Source | Reason |
|----------|--------------------|--------|
| Production (internet access) | OneMap Planning Areas API | Authoritative, official boundaries |
| CI / offline testing | Bundled `zones-seed.json` | No network dependency |
| Custom zone needed | Admin SQL `INSERT` or future admin API | One-off additions |
| Road/highway geometries | OSM Overpass API or bundled WKT | URA doesn't publish road LineStrings |

---

## 10. Verification Queries

After initialization, run these against the database to confirm correctness:

```sql
-- 1. Count by category
SELECT category, COUNT(*) FROM zones GROUP BY category;

-- 2. Verify CBD polygon is in Singapore bounds
SELECT name, ST_AsText(geog::geometry) FROM zones WHERE name = 'CBD';

-- 3. Spot-check that a known taxi coordinate falls inside CBD
--    (use a coordinate from a recent taxi_positions row in the CBD area)
SELECT ST_Within(
    ST_SetSRID(ST_MakePoint(103.8525, 1.2843), 4326),   -- Raffles Place
    (SELECT geog::geometry FROM zones WHERE name = 'CBD')
);
-- Expected: true

-- 4. Verify road zone is a LineString
SELECT name, ST_GeometryType(geog::geometry) FROM zones WHERE category = 'highway';
-- Expected: ST_LineString
```

---

## 11. Implementation Sequence

```
Step 1  Create Zone.java domain entity
Step 2  Create ZoneRepository.java (hasAnyZone, existsByName, batchInsert)
Step 3  Create zones-seed.json with CBD + all expressways as baseline
Step 4  Create ZoneSeedService.java (file loader first, OneMap fetch second)
Step 5  Create ZoneDataInitializer.java (ApplicationRunner)
Step 6  Add zone.seed config block to application.yml
Step 7  Update TaxiQueryService.countInZone() to check existence → 404
Step 8  (Optional) Add GET /api/v1/zones listing endpoint
Step 9  Run verification queries against dev DB
Step 10 Enrich zones-seed.json with full polygon list once OneMap fetch is validated
```

---

## 12. Out of Scope

- Zone geometry editing UI
- Dynamic zone creation via REST API (write path)
- Zone versioning / history
- Automatic zone boundary updates when URA publishes new master plan data
