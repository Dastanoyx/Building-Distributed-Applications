package dev.orderflow.order.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orderflow.order.shared.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way this service publishes anything (Session 09).
 *
 * <p>It does <b>not</b> talk to Kafka. It inserts a row, inside whatever transaction the caller
 * is already running, and returns immediately. That is the whole trick: publishing becomes a
 * local write, so it either commits with the business change or disappears with it.</p>
 */
@Component
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Queues an event for publication.
     *
     * @param topic       destination topic
     * @param aggregateId used as the Kafka key, so ordering per aggregate is preserved
     * @param event       any serialisable record from {@code common-events}
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void publish(String topic, String aggregateType, String aggregateId, Object event) {
        try {
            repository.save(new OutboxRecord(
                    aggregateType,
                    aggregateId,
                    event.getClass().getSimpleName(),
                    topic,
                    objectMapper.writeValueAsString(event),
                    MDC.get(CorrelationIdFilter.MDC_KEY)));
        } catch (JsonProcessingException e) {
            // Serialisation failure is a programming error: fail the transaction loudly.
            throw new IllegalStateException("cannot serialise event " + event.getClass(), e);
        }
    }
}
