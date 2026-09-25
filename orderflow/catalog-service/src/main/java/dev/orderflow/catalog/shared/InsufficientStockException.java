package dev.orderflow.catalog.shared;

/**
 * Not enough units to satisfy a reservation.
 *
 * <p>This is a <b>business outcome</b>, not a technical failure: the request was valid and the
 * system is healthy, the answer is simply « no ». That distinction matters downstream — the
 * order service must not retry it and the circuit breaker must not count it as a failure
 * (Session 06). Carrying {@code requested} and {@code available} lets the caller explain the
 * refusal to a human without a second round trip.</p>
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
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
