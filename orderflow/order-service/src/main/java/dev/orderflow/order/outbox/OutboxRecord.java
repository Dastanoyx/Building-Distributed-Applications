package dev.orderflow.order.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An event waiting to be published (Session 09).
 *
 * <p>This row is written <b>in the same transaction</b> as the business change. That single
 * fact removes two failure modes that no amount of retrying can fix:</p>
 * <ul>
 *   <li>the order is committed but the event is lost because the broker was down;</li>
 *   <li>the event is published but the transaction rolls back, announcing an order that
 *       does not exist.</li>
 * </ul>
 *
 * <p>A separate drainer sends the row afterwards. If the broker is down, the row simply waits.</p>
 */
@Entity
@Table(name = "outbox")
public class OutboxRecord {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    /** Becomes the Kafka key, so every event of one order lands in the same partition. */
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String topic;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null while the event is still waiting. The partial index only covers these rows. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected OutboxRecord() {
    }

    public OutboxRecord(String aggregateType, String aggregateId, String eventType,
                        String topic, String payload, String correlationId) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
        this.attempts = 0;
    }

    /** Called only after the broker has acknowledged the send. */
    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void failedAttempt() {
        this.attempts++;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getAttempts() {
        return attempts;
    }
}
