-- Session 09 — the transactional outbox.
--
-- The partial index is deliberate: it only covers rows that are still waiting, so it stays
-- small forever even when the table holds millions of published events.

CREATE TABLE outbox (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id   VARCHAR(64) NOT NULL,
    event_type     VARCHAR(60) NOT NULL,
    topic          VARCHAR(40) NOT NULL,
    payload        TEXT        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    attempts       INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX ix_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_aggregate ON outbox (aggregate_id);
