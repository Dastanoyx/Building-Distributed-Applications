-- Session 05 — idempotency keys.
--
-- The PRIMARY KEY is the mechanism: claiming a key is INSERT ... ON CONFLICT DO NOTHING,
-- so exactly one concurrent caller can own the work.

CREATE TABLE idempotency_record (
    key             VARCHAR(80) PRIMARY KEY,
    fingerprint     VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body   TEXT,
    state           VARCHAR(12) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_idempotency_expires ON idempotency_record (expires_at);
