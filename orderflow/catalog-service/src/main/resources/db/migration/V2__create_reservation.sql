-- Session 03 / 05 — stock held for an order.
--
-- The unique constraint is the idempotency mechanism: a retried reservation for the same
-- (order_id, sku) loses against this index instead of holding the stock a second time.

CREATE TABLE reservation (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL,
    sku        VARCHAR(20) NOT NULL REFERENCES product(sku),
    quantity   INTEGER     NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_order_sku UNIQUE (order_id, sku)
);

CREATE INDEX ix_reservation_order ON reservation (order_id);
