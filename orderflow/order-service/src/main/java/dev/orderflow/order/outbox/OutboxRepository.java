package dev.orderflow.order.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Persistence for the outbox. */
public interface OutboxRepository extends JpaRepository<OutboxRecord, UUID> {

    /**
     * Claims a batch of unpublished events.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} is what lets several replicas drain the outbox at the
     * same time: each one locks the rows it takes and skips the rows another instance already
     * holds. Without {@code SKIP LOCKED} the replicas queue behind each other; without
     * {@code FOR UPDATE} they publish duplicates (Sessions 09 and 13).</p>
     */
    @Query(value = """
                   SELECT * FROM outbox
                   WHERE published_at IS NULL
                   ORDER BY created_at
                   LIMIT :batchSize
                   FOR UPDATE SKIP LOCKED
                   """, nativeQuery = true)
    List<OutboxRecord> claimUnpublished(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    long countByAggregateId(String aggregateId);
}
