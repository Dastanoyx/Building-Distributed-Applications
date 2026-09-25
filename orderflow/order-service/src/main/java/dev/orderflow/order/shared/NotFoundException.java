package dev.orderflow.order.shared;

/** The order does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
