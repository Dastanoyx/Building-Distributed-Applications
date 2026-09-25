-- Session 09 — saga state, persisted so a crash cannot lose a half-finished workflow.

CREATE TABLE order_saga (
    order_id           UUID PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    state              VARCHAR(20) NOT NULL,
    last_transition_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts           INTEGER     NOT NULL DEFAULT 0,
    failure_reason     VARCHAR(200)
);

-- The sweeper's query: "which sagas have not moved recently?"
CREATE INDEX ix_saga_state_transition ON order_saga (state, last_transition_at);
