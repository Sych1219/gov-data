# Zone Initialization Design Document

> **Cross-repo docs** — when updating this file, also check:
> - `civic-app` → `design-docs/MVP-taxi-spatial-qa.md` — PLANNING AREAS in system prompt (§6.3) must match zone names seeded here
> - `civic-frontend` → `docs/apis-data-contract.md` — `ZoneGeometryData` shape and `context.type = "zone"` fields reference zone categories
> - Full index: `civic-frontend/docs/cross-repo-index.md`

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
| OneMap Planning Areas | `https://www.onemap.gov.sg/api/public/popapi/getAllPlanningarea` |
| OneMap Subzones | `https://www.onemap.gov.sg/api/public/geodata/SubzonesGeo` |
| URA GeoJSON (static) | `https://data.gov.sg/datasets?query=planning+area` |

Response is a JSON object `{"SearchResults": [...]}` where each element has
`pln_area_n` (planning area name, uppercase) and `geojson` (a geometry string,
e.g. `MultiPolygon`). Requires a Bearer token in the `Authorization` header.

### 3.2 Road/Highway LineStrings — OpenStreetMap (Overpass API)

Expressways and arterial roads are discovered **dynamically** by querying OSM for all
named ways matching a given `highway` type tag within Singapore's bounding box
`(1.1,103.5,1.5,104.1)`. No hard-coded name list is used — zone name = `tags.name`
from OSM directly, covering every named road returned (e.g. "Pan Island Expressway",
"Upper Serangoon Road", "Punggol Walk").

```
Overpass API: https://overpass-api.de/api/interpreter
```

Two tag-based queries are executed at startup:

```
# All expressways in Singapore → category = 'highway'
[out:json];
way["highway"="motorway"]["name"](1.1,103.5,1.5,104.1);
out geom;

# All major arterial roads in Singapore → category = 'road'
[out:json];
way["highway"~"trunk|primary"]["name"](1.1,103.5,1.5,104.1);
out geom;
```

The `["name"]` filter excludes unnamed ramps and slip roads. Each response contains
multiple `way` elements. Ways sharing the same `tags.name` are grouped and their
coordinate sequences combined into a single `MULTILINESTRING` WKT per zone entry.

---

## 4. Zone Catalog

All 55 URA planning area names are used directly as returned by the OneMap API (uppercase).
No canonical remapping is applied.

### 4.1 Districts (`category = 'district'`)

All planning areas sourced from `getAllPlanningarea` (55 total):

| `name` |
|--------|
| `ANG MO KIO` |
| `BEDOK` |
| `BISHAN` |
| `BOON LAY` |
| `BUKIT BATOK` |
| `BUKIT MERAH` |
| `BUKIT PANJANG` |
| `BUKIT TIMAH` |
| `CENTRAL WATER CATCHMENT` |
| `CHANGI` |
| `CHANGI BAY` |
| `CHOA CHU KANG` |
| `CLEMENTI` |
| `DOWNTOWN CORE` |
| `GEYLANG` |
| `HOUGANG` |
| `JURONG EAST` |
| `JURONG WEST` |
| `KALLANG` |
| `LIM CHU KANG` |
| `MANDAI` |
| `MARINA EAST` |
| `MARINA SOUTH` |
| `MARINE PARADE` |
| `MUSEUM` |
| `NEWTON` |
| `NORTH-EASTERN ISLANDS` |
| `NOVENA` |
| `ORCHARD` |
| `OUTRAM` |
| `PASIR RIS` |
| `PAYA LEBAR` |
| `PIONEER` |
| `PUNGGOL` |
| `QUEENSTOWN` |
| `RIVER VALLEY` |
| `ROCHOR` |
| `SELETAR` |
| `SEMBAWANG` |
| `SENGKANG` |
| `SERANGOON` |
| `SIMPANG` |
| `SINGAPORE RIVER` |
| `SOUTHERN ISLANDS` |
| `STRAITS VIEW` |
| `SUNGEI KADUT` |
| `TAMPINES` |
| `TANGLIN` |
| `TENGAH` |
| `TOA PAYOH` |
| `TUAS` |
| `WESTERN ISLANDS` |
| `WESTERN WATER CATCHMENT` |
| `WOODLANDS` |
| `YISHUN` |

### 4.2 Roads (`category = 'road'`)

Roads are discovered **dynamically** at startup by querying OSM for all ways tagged
`highway=trunk` or `highway=primary` within Singapore's bounding box.
Zone `name` = `tags.name` as returned by OSM. No canonical remapping is applied.

Examples of roads that will be discovered: "Orchard Road", "Thomson Road",
"Upper Serangoon Road", "Punggol Walk", "Shenton Way", "Beach Road", and all
other named trunk/primary roads in Singapore.

### 4.3 Highways (`category = 'highway'`)

Highways are discovered **dynamically** at startup by querying OSM for all ways tagged
`highway=motorway` within Singapore's bounding box.
Zone `name` = `tags.name` as returned by OSM.

Examples of highways that will be discovered: "Pan Island Expressway",
"Central Expressway", "East Coast Parkway", "Ayer Rajah Expressway", and all
other named motorways in Singapore.

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
       │       └─ Seed Source:
       │           OneMap Planning Areas API  (authoritative)
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
    private final WebClient.Builder webClientBuilder;
    private final String onemapUrl;        // from config
    private final String onemapToken;      // from config
    private final String overpassUrl;      // from config
    private final String overpassBbox;     // from config, e.g. "1.1,103.5,1.5,104.1"

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

    /**
     * Fetches districts (OneMap) and roads/highways (Overpass) in parallel,
     * merges the results, and batch-inserts into the zones table.
     */
    private Mono<Void> loadAndInsert() {
        return Mono.zip(fetchFromOnemap(), fetchFromOverpass())
                .map(t -> { List<ZoneSeedEntry> all = new ArrayList<>(t.getT1());
                            all.addAll(t.getT2()); return all; })
                .flatMapMany(zoneRepository::batchInsert)
                .doOnNext(n -> log.debug("inserted {} zone row(s)", n))
                .then()
                .doOnSuccess(v -> log.info("zone seed complete"));
    }

    /** Fetch Singapore planning areas from OneMap and convert to WKT entries. */
    private Mono<List<ZoneSeedEntry>> fetchFromOnemap() { ... }

    /**
     * Fetch all named expressways (highway=motorway) and arterial roads
     * (highway=trunk|primary) from OSM Overpass API.
     *
     * Strategy:
     *   1. POST two Overpass QL queries (one per highway type class).
     *   2. Parse each response's elements[].{tags.name, geometry[]{lat,lon}}.
     *   3. Skip any way whose tags.name is blank (unnamed ramps / slip roads).
     *   4. Group ways by tags.name → Map<String, List<wayCoords>>.
     *   5. Per road name: build MULTILINESTRING((lon lat,...),(lon lat,...)) WKT.
     *      Note: Overpass returns {lat,lon}; WKT requires (lon lat) — swap the pair.
     *   6. Return combined List<ZoneSeedEntry> for all discovered roads/highways.
     *
     * On error: log warning and return empty list so district seeding is not blocked.
     */
    private Mono<List<ZoneSeedEntry>> fetchFromOverpass() {
        Mono<List<ZoneSeedEntry>> highways = fetchOverpassByType(
                "way[\"highway\"=\"motorway\"][\"name\"](" + overpassBbox + ");",
                "highway");
        Mono<List<ZoneSeedEntry>> roads = fetchOverpassByType(
                "way[\"highway\"~\"trunk|primary\"][\"name\"](" + overpassBbox + ");",
                "road");
        return Mono.zip(highways, roads)
                .map(t -> { List<ZoneSeedEntry> all = new ArrayList<>(t.getT1());
                            all.addAll(t.getT2()); return all; })
                .onErrorResume(ex -> { log.warn("Overpass fetch failed: {}", ex.getMessage()); return Mono.just(List.of()); });
    }

    /**
     * Executes one Overpass QL query, groups resulting ways by tags.name,
     * and builds one ZoneSeedEntry (MULTILINESTRING WKT) per named road.
     */
    private Mono<List<ZoneSeedEntry>> fetchOverpassByType(String qlBody, String category) { ... }
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

### 7.5 Fix Silent Zero in `TaxiQueryService.java`

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

### 7.6 Optional: `GET /api/v1/zones` listing endpoint

A lightweight endpoint to let callers discover valid zone names:

```
GET /api/v1/zones?category=district        → list district names
GET /api/v1/zones?category=highway         → list highway names
GET /api/v1/zones                          → all zones
```

Response:
```json
{
  "success": true,
  "data": {
    "zones": [
      { "name": "CBD",      "category": "district" },
      { "name": "Tampines", "category": "district" },
      { "name": "PIE",      "category": "highway"  }
    ]
  }
}
```

---

## 8. `application.yml` Additions

```yaml
zone:
  seed:
    onemap:
      planning-areas-url: https://www.onemap.gov.sg/api/public/popapi/getAllPlanningarea
      token: <onemap-jwt-token>
    overpass:
      url: https://overpass-api.de/api/interpreter
      bounding-box: "1.1,103.5,1.5,104.1"   # south,west,north,east — covers all of Singapore
```

---

## 9. Geometry Source Decision Matrix

| Scenario | Recommended Source | Reason |
|----------|--------------------|--------|
| Production | OneMap Planning Areas API | Authoritative, official boundaries |
| Custom zone needed | Admin SQL `INSERT` or future admin API | One-off additions |
| Road/highway geometries | OSM Overpass API (tag-based discovery, all named ways) | URA doesn't publish road LineStrings; Overpass covers all named roads dynamically |

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

-- 4. Verify highway zones are MultiLineStrings (multiple OSM way segments merged per road)
SELECT name, ST_GeometryType(geog::geometry) FROM zones WHERE category = 'highway';
-- Expected: ST_MultiLineString

-- 5. Spot-check a known PIE coordinate falls within 500m of the stored geometry
SELECT ST_DWithin(
    ST_SetSRID(ST_MakePoint(103.72, 1.335), 4326)::geography,
    (SELECT geog FROM zones WHERE name = 'Pan Island Expressway'),
    500
);
-- Expected: true
```

---

## 11. Implementation Sequence

```
Step 1  Create Zone.java domain entity
Step 2  Create ZoneRepository.java (hasAnyZone, existsByName, batchInsert)
Step 3  Create ZoneSeedService.java (OneMap district fetch + Overpass tag-based road/highway fetch → WKT insert)
Step 4  Create ZoneDataInitializer.java (ApplicationRunner)
Step 5  Add zone.seed config block to application.yml
Step 6  Update TaxiQueryService.countInZone() to check existence → 404
Step 7  (Optional) Add GET /api/v1/zones listing endpoint
Step 8  Run verification queries against dev DB
```

---

## 12. Out of Scope

- Zone geometry editing UI
- Dynamic zone creation via REST API (write path)
- Zone versioning / history
- Automatic zone boundary updates when URA publishes new master plan data
