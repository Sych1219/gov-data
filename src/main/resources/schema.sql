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

-- ── Individual taxi positions ─────────────────────────────────────────────────
-- geog is a GEOGRAPHY (not GEOMETRY) generated column so that ST_DWithin
-- distances are automatically in metres. R2DBC entities never map this column.
CREATE TABLE IF NOT EXISTS taxi_positions (
    id          BIGSERIAL  PRIMARY KEY,
    snapshot_id BIGINT     NOT NULL REFERENCES taxi_snapshots(id) ON DELETE CASCADE,
    longitude   FLOAT8     NOT NULL,
    latitude    FLOAT8     NOT NULL,
    geog        GEOGRAPHY(POINT, 4326)
                    GENERATED ALWAYS AS (
                        ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                    ) STORED
);

CREATE INDEX IF NOT EXISTS idx_taxi_positions_snapshot_id
    ON taxi_positions (snapshot_id);

CREATE INDEX IF NOT EXISTS idx_taxi_positions_geog
    ON taxi_positions USING GIST (geog);

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
