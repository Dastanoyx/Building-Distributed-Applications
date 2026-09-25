package dev.orderflow.catalog.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock held for one order line.
 *
 * <p>The unique constraint on {@code (order_id, sku)} is the mechanism that makes reservation
 * <b>idempotent</b>: if the same reservation request arrives twice — a retry after a lost
 * reply, a redelivered message — the second insert loses against the index and the service
 * replays the first outcome instead of holding the stock twice (Sessions 05 and 08).</p>
 */
@Entity
@Table(name = "reservation",
       uniqueConstraints = @UniqueConstraint(name = "uq_reservation_order_sku",
                                             columnNames = {"order_id", "sku"}))
public class Reservation {

    /** Lifecycle of a reservation: held until the order is confirmed, or released on compensation. */
    public enum Status { HELD, RELEASED }

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 20)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reservation() {
    }

    public Reservation(UUID orderId, String sku, int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = Status.HELD;
        this.createdAt = Instant.now();
    }

    /**
     * Marks the reservation released.
     *
     * @return true if this call actually changed the state; false if it was already released,
     *         which is what makes a repeated compensation harmless (Session 09)
     */
    public boolean release() {
        if (status == Status.RELEASED) {
            return false;
        }
        status = Status.RELEASED;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Status getStatus() {
        return status;
    }
}
