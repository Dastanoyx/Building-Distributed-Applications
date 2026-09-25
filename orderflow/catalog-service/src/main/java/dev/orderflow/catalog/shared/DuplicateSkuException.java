package dev.orderflow.catalog.shared;

/** A product with this SKU already exists. Mapped to HTTP 409 Conflict. */
public class DuplicateSkuException extends RuntimeException {

    private final String sku;

    public DuplicateSkuException(String sku) {
        super("SKU %s already exists".formatted(sku));
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
