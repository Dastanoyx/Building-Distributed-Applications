-- Session 03 — orders and their lines.

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    customer_id         VARCHAR(64)   NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    total               NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    cancellation_reason VARCHAR(200),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE order_line (
    id         UUID PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku        VARCHAR(20)   NOT NULL,
    quantity   INTEGER       NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

-- The index that turns the customer's order list from a sequential scan into an index scan.
-- Session 12 measures the difference: 61 ms -> 0.2 ms on 50k rows.
CREATE INDEX ix_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX ix_order_line_order ON order_line (order_id);
