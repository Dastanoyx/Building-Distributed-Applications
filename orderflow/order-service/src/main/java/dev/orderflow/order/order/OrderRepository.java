package dev.orderflow.order.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence for orders. */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Loads an order together with its lines in ONE query.
     *
     * <p>Without the entity graph this is the textbook N+1: one query for the orders, then one
     * per order to fetch its lines. Invisible with three rows in development, fatal with three
     * thousand in production (Session 03).</p>
     */
    @EntityGraph(attributePaths = "lines")
    Optional<Order> findWithLinesById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    long countByStatus(OrderStatus status);
}
