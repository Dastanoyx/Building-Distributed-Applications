-- Session 08 — notifications and the deduplication table.

CREATE TABLE notification (
    id          UUID PRIMARY KEY,
    order_id    UUID         NOT NULL,
    customer_id VARCHAR(64)  NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    message     VARCHAR(300) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_notification_customer ON notification (customer_id, created_at DESC);
CREATE INDEX ix_notification_order ON notification (order_id);

-- One row per event already applied. This is what turns at-least-once DELIVERY into
-- exactly-once EFFECT (Session 08).
CREATE TABLE processed_event (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_processed_event_at ON processed_event (processed_at);
