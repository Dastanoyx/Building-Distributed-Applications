package dev.orderflow.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything that can happen to an order, as a closed set of facts.
 *
 * <p>A {@code sealed} interface means the compiler knows every possible implementation, so a
 * {@code switch} over an OrderEvent is exhaustive: add a new event and every consumer that
 * forgot to handle it fails to compile instead of silently ignoring it.</p>
 *
 * <p>Three rules apply to every event in this project:</p>
 * <ol>
 *   <li><b>Past tense.</b> An event is something that already happened and cannot be refused.</li>
 *   <li><b>Its own id.</b> {@code eventId} lets a consumer recognise a redelivery (Session 08).</li>
 *   <li><b>Additive changes only.</b> Add optional fields; never change the meaning of one.
 *       {@code version} records the shape so consumers can adapt.</li>
 * </ol>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderEvent.OrderPlaced.class, name = "OrderPlaced"),
        @JsonSubTypes.Type(value = OrderEvent.OrderConfirmed.class, name = "OrderConfirmed"),
        @JsonSubTypes.Type(value = OrderEvent.OrderCancelled.class, name = "OrderCancelled")
})
public sealed interface OrderEvent {

    /** Unique id of this event instance; the deduplication key for consumers. */
    UUID eventId();

    /** The aggregate this event is about. Used as the Kafka key, so order is preserved per order. */
    UUID orderId();

    /** When the fact happened, as recorded by the producing service. */
    Instant occurredAt();

    /** One line of an order, copied into the event so consumers need no callback. */
    record Line(String sku, int quantity, BigDecimal unitPrice) {
    }

    /** A customer placed an order. Stock is reserved, payment has not happened yet. */
    record OrderPlaced(UUID eventId, UUID orderId, String customerId, List<Line> lines,
                       BigDecimal total, Instant occurredAt, int version) implements OrderEvent {

        public static OrderPlaced of(UUID orderId, String customerId, List<Line> lines, BigDecimal total) {
            return new OrderPlaced(UUID.randomUUID(), orderId, customerId, List.copyOf(lines),
                    total, Instant.now(), 1);
        }
    }

    /** Payment succeeded; the order is final. */
    record OrderConfirmed(UUID eventId, UUID orderId, String customerId, BigDecimal total,
                          Instant occurredAt, int version) implements OrderEvent {

        public static OrderConfirmed of(UUID orderId, String customerId, BigDecimal total) {
            return new OrderConfirmed(UUID.randomUUID(), orderId, customerId, total, Instant.now(), 1);
        }
    }

    /** The order was cancelled; any reserved stock has been (or is being) released. */
    record OrderCancelled(UUID eventId, UUID orderId, String customerId, String reason,
                          Instant occurredAt, int version) implements OrderEvent {

        public static OrderCancelled of(UUID orderId, String customerId, String reason) {
            return new OrderCancelled(UUID.randomUUID(), orderId, customerId, reason, Instant.now(), 1);
        }
    }
}
