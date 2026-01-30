CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS gov_records
(
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL
);

    CREATE TABLE IF NOT EXISTS gov_api_registration
    (
        id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        name              VARCHAR(255)        NOT NULL,
        description       TEXT,
        base_url          VARCHAR(512)        NOT NULL,
        http_method       VARCHAR(20)         NOT NULL,
        headers_json      TEXT,
        query_params_json TEXT,
        body_params_json  TEXT,
        created_at        TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
        CONSTRAINT uk_gov_api_registration_name_url UNIQUE (name, base_url)
    );
-- OpenAPI Registration Tables for v2
CREATE TABLE IF NOT EXISTS gov_openapi_registration
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(255)        NOT NULL,
    title                VARCHAR(255)        NOT NULL,
    version              VARCHAR(50)         NOT NULL,
    description          TEXT,
    category             VARCHAR(100),
    base_url             VARCHAR(512)        NOT NULL,
    openapi_version      VARCHAR(20)         NOT NULL,
    openapi_spec_json    TEXT                NOT NULL,
    endpoint_count       INTEGER             DEFAULT 0,
    tags                 TEXT[],
    created_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_openapi_title_url UNIQUE (title, base_url)
);

CREATE INDEX IF NOT EXISTS idx_openapi_title ON gov_openapi_registration (title);
CREATE INDEX IF NOT EXISTS idx_openapi_category ON gov_openapi_registration (category);
CREATE INDEX IF NOT EXISTS idx_openapi_tags ON gov_openapi_registration USING GIN (tags);

CREATE TABLE IF NOT EXISTS gov_openapi_endpoint
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    openapi_id           UUID                NOT NULL REFERENCES gov_openapi_registration(id) ON DELETE CASCADE,
    path                 VARCHAR(512)        NOT NULL,
    http_method          VARCHAR(20)         NOT NULL,
    operation_id         VARCHAR(255),
    summary              TEXT,
    description          TEXT,
    parameters_json      TEXT,
    request_body_json    TEXT,
    responses_json       TEXT,
    security_json        TEXT,
    tags                 TEXT[],
    created_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_endpoint_path_method UNIQUE (openapi_id, path, http_method)
);

CREATE INDEX IF NOT EXISTS idx_endpoint_openapi_id ON gov_openapi_endpoint (openapi_id);
CREATE INDEX IF NOT EXISTS idx_endpoint_method ON gov_openapi_endpoint (http_method);