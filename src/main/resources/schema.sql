-- Enable PostGIS extension (idempotent)
CREATE EXTENSION IF NOT EXISTS postgis;

-- Enable pg_trgm for fuzzy zone name search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── Snapshot metadata (one row per API poll) ─────────────────────────────────
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
-- geog is a GEOGRAPHY generated column — R2DBC entities never map this column.
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

CREATE INDEX IF NOT EXISTS idx_zones_name
    ON zones (name);

CREATE INDEX IF NOT EXISTS idx_zones_name_trgm
    ON zones USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_zones_geog
    ON zones USING GIST (geog);

-- ── Traffic cameras (static registry) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cameras (
    id              BIGSERIAL PRIMARY KEY,
    camera_id       BIGINT NOT NULL UNIQUE,
    latitude        DECIMAL(12, 8) NOT NULL,
    longitude       DECIMAL(12, 8) NOT NULL,
    location_name   VARCHAR(255),
    expressway      VARCHAR(50),
    resolution      VARCHAR(20),
    first_seen_at   TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ
);

-- ── Camera snapshots (latest image per camera) ──────────────────────────────
CREATE TABLE IF NOT EXISTS camera_snapshots (
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

CREATE INDEX IF NOT EXISTS idx_snapshots_camera_time
    ON camera_snapshots (camera_id, timestamp DESC);
