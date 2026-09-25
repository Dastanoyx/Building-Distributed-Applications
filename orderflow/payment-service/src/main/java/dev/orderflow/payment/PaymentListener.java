package dev.orderflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orderflow.events.PaymentMessages;
import dev.orderflow.events.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorises payments (Sessions 08 and 09).
 *
 * <p>The rule is deliberately simple so the compensation path is easy to demonstrate: anything
 * strictly below {@code orderflow.payment.limit} is approved, anything above is declined.</p>
 *
 * <p>Two distributed-systems details matter more than the rule itself:</p>
 * <ul>
 *   <li><b>Idempotency.</b> Kafka delivers at least once, so this listener can see the same
 *       command twice. Charging twice is the worst possible bug here, so every processed
 *       {@code commandId} is remembered and a duplicate is answered with the first outcome.</li>
 *   <li><b>Always answer.</b> Whether the payment succeeds or fails, an event goes back. A saga
 *       that receives nothing is a saga that hangs until the sweeper times it out.</li>
 * </ul>
 *
 * <p>The in-memory {@code processed} map is fine for a teaching service with no database; a real
 * payment service stores it in a table, exactly like the notification service does.</p>
 */
@Component
public class PaymentListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentListener.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final BigDecimal limit;

    /** commandId -> the answer we already produced, so a redelivery is replayed, not re-charged. */
    private final Map<UUID, String> processed = new ConcurrentHashMap<>();

    public PaymentListener(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper,
                           MeterRegistry meters,
                           @Value("${orderflow.payment.limit:500.00}") BigDecimal limit) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.limit = limit;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    public void onProcessPayment(String payload) throws Exception {
        PaymentMessages.ProcessPayment command =
                objectMapper.readValue(payload, PaymentMessages.ProcessPayment.class);

        String previous = processed.get(command.commandId());
        if (previous != null) {
            log.info("duplicate payment command {} for order {} — replaying the first answer",
                    command.commandId(), command.orderId());
            publish(command.orderId(), previous);
            return;
        }

        boolean approved = command.amount().compareTo(limit) < 0;
        Object answer = approved
                ? PaymentMessages.PaymentSucceeded.of(command.orderId(), command.amount(),
                        "AUTH-" + UUID.randomUUID().toString().substring(0, 8))
                : PaymentMessages.PaymentFailed.of(command.orderId(), command.amount(),
                        "amount %s exceeds the limit of %s".formatted(command.amount(), limit));

        String json = objectMapper.writeValueAsString(answer);
        processed.put(command.commandId(), json);

        meters.counter("orderflow.payments", "outcome", approved ? "approved" : "declined").increment();
        log.info("payment for order {} of {} → {}", command.orderId(), command.amount(),
                approved ? "APPROVED" : "DECLINED");

        publish(command.orderId(), json);
    }

    private void publish(UUID orderId, String json) {
        // Key = order id, so the saga receives payment events for one order in order.
        kafka.send(Topics.PAYMENT_EVENTS, orderId.toString(), json);
    }
}
