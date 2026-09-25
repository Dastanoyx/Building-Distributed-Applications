package dev.orderflow.catalog.shared;

/** The caller asked for something that does not exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
