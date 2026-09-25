package dev.orderflow.order.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/** Persistence for idempotency keys. */
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Claims a key atomically.
     *
     * @return 1 if this caller now owns the work, 0 if somebody else already claimed it.
     *         The database decides; no read-then-write, therefore no race (Session 05).
     */
    @Modifying
    @Query(value = """
                   INSERT INTO idempotency_record (key, fingerprint, state, created_at, expires_at)
                   VALUES (:key, :fingerprint, 'IN_PROGRESS', now(), :expiresAt)
                   ON CONFLICT (key) DO NOTHING
                   """, nativeQuery = true)
    int claim(@Param("key") String key,
              @Param("fingerprint") String fingerprint,
              @Param("expiresAt") Instant expiresAt);

    /** Housekeeping: keys older than their expiry can never be replayed, so they are removed. */
    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
