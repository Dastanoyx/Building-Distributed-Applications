package dev.orderflow.catalog.product;

import dev.orderflow.catalog.shared.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A product, with the one rule that matters in this service: <b>stock is never negative</b>.
 *
 * <p>Notice there is no {@code setStock}. The only ways to change the stock are
 * {@link #reserve(int)} and {@link #release(int)}, which enforce the rule. That is the
 * difference between a domain object and a data bag, and it is what makes the rule
 * testable without Spring, a database, or a running application.</p>
 *
 * <p>{@link Version} enables optimistic locking: if two transactions load the same row and
 * both write, the second one fails instead of silently overwriting the first (Session 03).
 * The hot path does not rely on it — {@code ProductRepository.tryReserve} does the work in a
 * single conditional UPDATE — but it protects every other write.</p>
 */
@Entity
@Table(name = "product")
public class Product {

    /** SKUs look like ABC-123. Validated here so a bad value cannot enter the domain at all. */
    public static final String SKU_PATTERN = "[A-Z]{3}-\\d{3}";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. Never call it from your own code. */
    protected Product() {
    }

    public Product(String sku, String name, BigDecimal price, int stock) {
        if (sku == null || !sku.matches(SKU_PATTERN)) {
            throw new IllegalArgumentException("SKU must look like ABC-123, got: " + sku);
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("stock cannot be negative");
        }
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.name = Objects.requireNonNull(name, "name");
        this.price = price;
        this.stock = stock;
        this.createdAt = Instant.now();
    }

    /**
     * Takes {@code quantity} units out of stock.
     *
     * @throws InsufficientStockException if there are not enough units; the stock is left untouched
     */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > stock) {
            throw new InsufficientStockException(sku, quantity, stock);
        }
        stock -= quantity;
    }

    /** Puts units back, for example when a saga compensates a cancelled order (Session 09). */
    public void release(int quantity) {
        requirePositive(quantity);
        stock += quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public boolean isInStock() {
        return stock > 0;
    }

    @Override
    public String toString() {
        return "Product[%s %s %s, stock=%d]".formatted(sku, name, price, stock);
    }
}
