package dev.orderflow.order.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The memory that makes a retried POST safe (Session 05).
 *
 * <p>The key is supplied by the client and is the primary key here, so claiming it is a single
 * {@code INSERT ... ON CONFLICT DO NOTHING}: exactly one caller can own the work even if a
 * thousand duplicates arrive in the same millisecond. Checking « does the key exist? » and then
 * inserting would reintroduce the very race this table removes.</p>
 *
 * <p>{@code fingerprint} is a hash of the request body. Same key with a different body is a
 * client bug, and answering with the first result would hide it — so it is reported as 409.</p>
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    public enum State { IN_PROGRESS, COMPLETED }

    @Id
    @Column(length = 80)
    private String key;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(nullable = false, length = 12)
    private String state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String fingerprint, Instant expiresAt) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.state = State.IN_PROGRESS.name();
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void complete(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
        this.state = State.COMPLETED.name();
    }

    public boolean matches(String otherFingerprint) {
        return fingerprint.equals(otherFingerprint);
    }

    public boolean isCompleted() {
        return State.COMPLETED.name().equals(state);
    }

    public String getKey() {
        return key;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
