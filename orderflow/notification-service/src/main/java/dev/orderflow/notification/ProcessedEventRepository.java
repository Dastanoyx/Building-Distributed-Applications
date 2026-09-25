package dev.orderflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Deduplication store for consumed events. */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Records an event id if it is new.
     *
     * @return 1 when this delivery is the first one (process it), 0 when it is a duplicate
     *         (skip it). The primary key does the work; no read-then-write, so no race.
     */
    @Modifying
    @Query(value = """
                   INSERT INTO processed_event (event_id, processed_at)
                   VALUES (:eventId, now())
                   ON CONFLICT (event_id) DO NOTHING
                   """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId);

    /**
     * Housekeeping: once an event is older than the topic retention it can never be
     * redelivered, so its deduplication row is dead weight.
     */
    @Modifying
    @Query("delete from ProcessedEvent p where p.processedAt < :threshold")
    int deleteProcessedBefore(@Param("threshold") Instant threshold);
}
