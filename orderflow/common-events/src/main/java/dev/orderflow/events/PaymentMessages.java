package dev.orderflow.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The command sent to the payment service and the two facts it can produce.
 *
 * <p>Note the asymmetry, which is deliberate and is the heart of Session 08:
 * a <b>command</b> is addressed to exactly one service and may be refused;
 * an <b>event</b> is a fact broadcast to anyone interested and cannot be refused.</p>
 */
public final class PaymentMessages {

    /**
     * « Please take payment for this order. »
     *
     * @param commandId id of this command instance, used by the payment service to ignore
     *                  a redelivery instead of charging twice (Session 08)
     */
    public record ProcessPayment(UUID commandId, UUID orderId, String customerId,
                                 BigDecimal amount, Instant issuedAt) {

        public static ProcessPayment of(UUID orderId, String customerId, BigDecimal amount) {
            return new ProcessPayment(UUID.randomUUID(), orderId, customerId, amount, Instant.now());
        }
    }

    /** The money was taken. The saga can move to CONFIRMED. */
    public record PaymentSucceeded(UUID eventId, UUID orderId, BigDecimal amount,
                                   String authorisationCode, Instant occurredAt) {

        public static PaymentSucceeded of(UUID orderId, BigDecimal amount, String code) {
            return new PaymentSucceeded(UUID.randomUUID(), orderId, amount, code, Instant.now());
        }
    }

    /** The money was not taken. The saga must compensate: release the stock, cancel the order. */
    public record PaymentFailed(UUID eventId, UUID orderId, BigDecimal amount,
                                String reason, Instant occurredAt) {

        public static PaymentFailed of(UUID orderId, BigDecimal amount, String reason) {
            return new PaymentFailed(UUID.randomUUID(), orderId, amount, reason, Instant.now());
        }
    }

    private PaymentMessages() {
    }
}
