CREATE TABLE IF NOT EXISTS gov_records
(
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS gov_api_registration
(
    id                UUID PRIMARY KEY,
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
