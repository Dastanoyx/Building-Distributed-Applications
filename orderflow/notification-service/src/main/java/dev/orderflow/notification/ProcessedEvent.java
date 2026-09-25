package dev.orderflow.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The record of an event this service has already handled (Session 08).
 *
 * <p>Kafka guarantees <b>at least once</b>: after a crash, a record is redelivered. Exactly-once
 * delivery does not exist across the network — the consumer must apply the effect once. This
 * one-column table is that mechanism, and it is inserted in the <b>same transaction</b> as the
 * notification: two separate transactions would reopen the window they are meant to close.</p>
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
