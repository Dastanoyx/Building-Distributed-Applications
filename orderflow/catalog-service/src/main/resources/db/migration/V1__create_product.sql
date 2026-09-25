-- Session 03 — the initial schema.
--
-- Flyway applies this file exactly once and records its checksum. Never edit an applied
-- migration: correct it with a new numbered file. Every constraint below is a rule the
-- application cannot break even if a bug slips through the Java code.

CREATE TABLE product (
    id         UUID PRIMARY KEY,
    sku        VARCHAR(20)   NOT NULL UNIQUE,
    name       VARCHAR(120)  NOT NULL,
    price      NUMERIC(12,2) NOT NULL CHECK (price > 0),
    -- The safety net behind ReservationService: even a logic bug cannot oversell,
    -- it fails loudly instead of storing negative inventory.
    stock      INTEGER       NOT NULL CHECK (stock >= 0),
    version    BIGINT        NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Case-insensitive search by name is the most common query on this table.
CREATE INDEX ix_product_name_lower ON product (lower(name));
