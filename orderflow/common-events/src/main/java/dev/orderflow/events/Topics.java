package dev.orderflow.events;

/**
 * Topic names, in one place so a typo cannot silently create a new topic.
 *
 * <p>Naming convention used in this project: {@code <aggregate>-<kind>}. Events are facts
 * ({@code order-events}); commands are instructions addressed to one service
 * ({@code payment-commands}). Mixing the two in one topic makes consumers guess.</p>
 */
public final class Topics {

    /** Facts about orders: OrderPlaced, OrderConfirmed, OrderCancelled. Key = order id. */
    public static final String ORDER_EVENTS = "order-events";

    /** Dead letter topic for {@link #ORDER_EVENTS}: messages that could not be processed. */
    public static final String ORDER_EVENTS_DLT = "order-events-dlt";

    /** Commands sent to the payment service: ProcessPayment. Key = order id. */
    public static final String PAYMENT_COMMANDS = "payment-commands";

    /** Facts produced by the payment service: PaymentSucceeded, PaymentFailed. Key = order id. */
    public static final String PAYMENT_EVENTS = "payment-events";

    private Topics() {
    }
}
