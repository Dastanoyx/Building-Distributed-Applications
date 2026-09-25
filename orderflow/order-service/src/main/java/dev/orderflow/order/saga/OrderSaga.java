package dev.orderflow.order.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The saga's memory, stored in the database rather than held in a field (Session 09).
 *
 * <p>That choice is what makes the workflow survive a {@code kill -9}: a restarted instance
 * finds the saga exactly where it stopped and carries on. Anything kept in memory would be a
 * half-finished order that nobody will ever complete.</p>
 */
@Entity
@Table(name = "order_saga")
public class OrderSaga {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaState state;

    @Column(name = "last_transition_at", nullable = false)
    private Instant lastTransitionAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected OrderSaga() {
    }

    private OrderSaga(UUID orderId) {
        this.orderId = orderId;
        this.state = SagaState.STARTED;
        this.lastTransitionAt = Instant.now();
        this.attempts = 0;
    }

    public static OrderSaga start(UUID orderId) {
        return new OrderSaga(orderId);
    }

    public void stockReserved() {
        transitionTo(SagaState.STOCK_RESERVED);
    }

    public void paid() {
        transitionTo(SagaState.PAID);
    }

    public void completed() {
        transitionTo(SagaState.COMPLETED);
    }

    public void compensate(String reason) {
        this.failureReason = reason;
        transitionTo(SagaState.COMPENSATING);
    }

    public void cancelled() {
        transitionTo(SagaState.CANCELLED);
    }

    public void failed(String reason) {
        this.failureReason = reason;
        this.state = SagaState.FAILED;      // terminal escape hatch: always reachable
        this.lastTransitionAt = Instant.now();
    }

    public void retried() {
        this.attempts++;
        this.lastTransitionAt = Instant.now();
    }

    private void transitionTo(SagaState next) {
        if (!state.canMoveTo(next)) {
            throw new IllegalSagaTransitionException(orderId, state, next);
        }
        this.state = next;
        this.lastTransitionAt = Instant.now();
    }

    public boolean isIn(SagaState candidate) {
        return state == candidate;
    }

    public Duration stuckFor() {
        return Duration.between(lastTransitionAt, Instant.now());
    }

    public UUID getOrderId() {
        return orderId;
    }

    public SagaState getState() {
        return state;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    /** Thrown when a duplicate or out-of-order event tries an impossible transition. */
    public static class IllegalSagaTransitionException extends RuntimeException {
        public IllegalSagaTransitionException(UUID orderId, SagaState from, SagaState to) {
            super("Saga %s cannot move from %s to %s".formatted(orderId, from, to));
        }
    }
}
