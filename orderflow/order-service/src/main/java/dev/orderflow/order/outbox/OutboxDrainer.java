package dev.orderflow.order.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Moves rows from the outbox to Kafka (Session 09).
 *
 * <p>Design notes worth reading before changing anything here:</p>
 * <ul>
 *   <li>The send is <b>confirmed</b> ({@code get} with a timeout) before the row is marked
 *       published. Marking first would lose the event if the broker never received it.</li>
 *   <li>The loop stops at the first failure instead of skipping ahead, so events of one
 *       aggregate keep their order.</li>
 *   <li>Marking after sending means an event can be sent twice (crash in between). That is
 *       at-least-once delivery, which is why every consumer deduplicates (Session 08).</li>
 *   <li>In production, Debezium reading the write-ahead log replaces this polling loop; the
 *       table design stays identical.</li>
 * </ul>
 */
@Component
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    public OutboxDrainer(OutboxRepository repository,
                         KafkaTemplate<String, String> kafka,
                         @Value("${orderflow.outbox.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderflow.outbox.drain-interval-ms:500}")
    @Transactional
    public void drain() {
        List<OutboxRecord> batch = repository.claimUnpublished(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxRecord record : batch) {
            try {
                ProducerRecord<String, String> message =
                        new ProducerRecord<>(record.getTopic(), record.getAggregateId(), record.getPayload());
                message.headers().add("eventType", record.getEventType().getBytes(StandardCharsets.UTF_8));
                if (record.getCorrelationId() != null) {
                    message.headers().add("correlationId",
                            record.getCorrelationId().getBytes(StandardCharsets.UTF_8));
                }

                kafka.send(message).get(5, TimeUnit.SECONDS);   // wait for the broker's acknowledgement
                record.markPublished();
                log.debug("published {} for {}", record.getEventType(), record.getAggregateId());

            } catch (Exception e) {
                record.failedAttempt();
                log.error("outbox publish failed for {} ({} attempts), will retry on the next tick",
                        record.getId(), record.getAttempts(), e);
                break;   // keep ordering: do not publish later events before this one
            }
        }
    }

    /** Exposed as a gauge so « events waiting » is visible in Grafana (Session 11). */
    public long pendingCount() {
        return repository.countByPublishedAtIsNull();
    }
}
