package dev.orderflow.catalog.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for products.
 *
 * <p>The two {@code @Modifying} queries are the important part of this file. Reading a row,
 * deciding in Java, then writing it back is a <b>race condition</b>: two requests can both
 * read « 10 in stock », both decide there is enough, and both write 8 — selling twelve units
 * of ten. A single conditional UPDATE lets PostgreSQL do the test and the write in one
 * atomic statement, under a row lock it holds for microseconds (Session 03).</p>
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * Search with optional filters. Every filter is nullable, so one query serves the whole
     * search screen and PostgreSQL does the filtering — not the JVM (Session 03).
     */
    @Query("""
           select p from Product p
           where (:keyword is null
                  or lower(p.name) like lower(concat('%', :keyword, '%'))
                  or lower(p.sku) like lower(concat('%', :keyword, '%')))
             and (:minPrice is null or p.price >= :minPrice)
             and (:maxPrice is null or p.price <= :maxPrice)
             and (:inStockOnly = false or p.stock > 0)
           """)
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("inStockOnly") boolean inStockOnly,
                         Pageable pageable);

    /**
     * Atomically takes units out of stock, but only if there are enough.
     *
     * @return 1 when the reservation succeeded, 0 when there was not enough stock
     *         (or the SKU does not exist). The caller decides which of the two it was.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity, p.version = p.version + 1 "
            + "where p.sku = :sku and p.stock >= :quantity")
    int tryReserve(@Param("sku") String sku, @Param("quantity") int quantity);

    /** Puts units back. Always safe: adding stock has no precondition. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity, p.version = p.version + 1 "
            + "where p.sku = :sku")
    int release(@Param("sku") String sku, @Param("quantity") int quantity);
}
