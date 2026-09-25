package dev.orderflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orderflow.events.OrderEvent;
import dev.orderflow.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * The Kafka entry point of this service (Session 08).
 *
 * <p>Keep a listener short. Whatever happens in here delays the next poll, and a listener that
 * takes longer than {@code max.poll.interval.ms} looks like a dead consumer to the broker,
 * which triggers a rebalance — the usual explanation for « my consumers keep restarting ».</p>
 *
 * <p>Throwing from this method means « do not commit the offset »: the record is retried by the
 * error handler and, after the configured attempts, published to the dead letter topic so the
 * partition keeps moving (see {@code KafkaErrorHandlingConfig}).</p>
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final NotificationService service;
    private final ObjectMapper objectMapper;

    public OrderEventListener(NotificationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "notification-service")
    public void onOrderEvent(@Payload String payload,
                             @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                             @Header(KafkaHeaders.OFFSET) long offset,
                             @Header(name = "correlationId", required = false) byte[] correlationId)
            throws Exception {

        // Restore the correlation id so these log lines join the HTTP request that caused them.
        MDC.put("correlationId", correlationId == null ? "" : new String(correlationId, StandardCharsets.UTF_8));
        try {
            OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
            log.debug("received {} from {}@{}", event.getClass().getSimpleName(), partition, offset);
            service.handle(event);
        } finally {
            MDC.clear();
        }
    }

    /**
     * The dead letter topic must be watched by somebody.
     *
     * <p>A DLT nobody looks at is a silent data-loss queue. This listener exists so the metric
     * below can be alerted on: {@code orderflow_dlt_messages_total > 0} is an incident.</p>
     */
    @KafkaListener(topics = Topics.ORDER_EVENTS_DLT, groupId = "notification-service-dlt")
    public void onDeadLetter(@Payload String payload,
                             @Header(name = "kafka_dlt-exception-message", required = false) byte[] reason) {
        log.error("DEAD LETTER: {} — reason: {}", payload,
                reason == null ? "unknown" : new String(reason, StandardCharsets.UTF_8));
    }
}
