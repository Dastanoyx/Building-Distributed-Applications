package dev.orderflow.order.client;

import java.math.BigDecimal;

/**
 * The three ways a catalog call can end badly, kept separate on purpose.
 *
 * <p>The distinction drives everything downstream: a business refusal must <b>not</b> be
 * retried and must <b>not</b> open the circuit breaker, while a technical failure must do both
 * (Session 06). Collapsing them into one exception is how teams end up retrying « insufficient
 * stock » two hundred times.</p>
 */
public final class CatalogExceptions {

    /** The SKU does not exist: the caller sent a bad order. Not retryable. */
    public static class ProductNotFound extends RuntimeException {
        private final String sku;

        public ProductNotFound(String sku) {
            super("Unknown SKU: " + sku);
            this.sku = sku;
        }

        public String getSku() {
            return sku;
        }
    }

    /** A valid request the catalog refuses because of the current stock. Not retryable. */
    public static class StockUnavailable extends RuntimeException {
        private final String sku;
        private final int requested;
        private final int available;

        public StockUnavailable(String sku, int requested, int available) {
            super("Only %d units of %s available, %d requested".formatted(available, sku, requested));
            this.sku = sku;
            this.requested = requested;
            this.available = available;
        }

        public String getSku() {
            return sku;
        }

        public int getRequested() {
            return requested;
        }

        public int getAvailable() {
            return available;
        }
    }

    /** The catalog is down, slow, or returned 5xx. Retryable, and it counts against the breaker. */
    public static class CatalogUnavailable extends RuntimeException {
        public CatalogUnavailable(String message) {
            super(message);
        }

        public CatalogUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Result of a successful reservation. */
    public record Reservation(String status, BigDecimal totalPrice) {
    }

    private CatalogExceptions() {
    }
}
