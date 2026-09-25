package dev.orderflow.catalog.product;

import dev.orderflow.catalog.product.dto.PageResponse;
import dev.orderflow.catalog.product.dto.ProductResponse;
import dev.orderflow.catalog.shared.DuplicateSkuException;
import dev.orderflow.catalog.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Application layer for products: use cases, transactions, business rules.
 *
 * <p>Dependencies arrive through the constructor, never through field injection. Two reasons:
 * the fields can be {@code final}, and a unit test can build this class with a stub repository
 * in one line, with no Spring context at all (Session 02).</p>
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final CatalogProperties properties;

    public ProductService(ProductRepository repository, CatalogProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Full-text-ish search with optional filters and pagination.
     *
     * <p>{@code readOnly = true} tells Hibernate to skip dirty checking and lets the query run
     * on a read replica if one is configured later (Session 12). The page size is clamped
     * rather than refused: an over-large value is usually a naive client, not an attack, and
     * the server — not the client — decides the ceiling.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, BigDecimal minPrice, BigDecimal maxPrice,
                                                boolean inStockOnly, int page, int size, String sortBy) {
        int effectiveSize = Math.min(size, properties.maxPageSize());
        Sort sort = switch (sortBy) {
            case "price" -> Sort.by("price").ascending();
            case "stock" -> Sort.by("stock").descending();
            default -> Sort.by("name").ascending();
        };
        Page<Product> found = repository.search(blankToNull(keyword), minPrice, maxPrice, inStockOnly,
                PageRequest.of(Math.max(0, page), effectiveSize, sort));
        return PageResponse.from(found, ProductResponse::from);
    }

    /** @throws NotFoundException if no product carries this SKU */
    @Transactional(readOnly = true)
    public Product requireBySku(String sku) {
        return repository.findBySku(sku)
                .orElseThrow(() -> new NotFoundException("Product %s not found".formatted(sku)));
    }

    /**
     * Creates a product.
     *
     * @throws DuplicateSkuException if the SKU is taken. The unique index is the real guard;
     *         this check only turns a database error into a meaningful message.
     */
    @Transactional
    public Product create(String sku, String name, BigDecimal price, int stock) {
        if (repository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        return repository.save(new Product(sku, name, price, stock));
    }

    /** Deletes a product. Idempotent: deleting an unknown SKU is not an error. */
    @Transactional
    public void delete(String sku) {
        Optional<Product> product = repository.findBySku(sku);
        product.ifPresent(repository::delete);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
