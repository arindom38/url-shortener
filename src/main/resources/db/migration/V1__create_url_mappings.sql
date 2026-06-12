CREATE TABLE url_mappings (
    id         BIGINT       PRIMARY KEY,
    short_code VARCHAR(10)  NOT NULL,
    long_url   TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT uq_url_mappings_short_code UNIQUE (short_code)
);

CREATE INDEX idx_url_mappings_short_code ON url_mappings (short_code);
