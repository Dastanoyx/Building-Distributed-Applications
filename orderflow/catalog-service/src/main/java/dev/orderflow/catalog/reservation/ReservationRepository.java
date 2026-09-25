package dev.orderflow.catalog.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for reservations. */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderIdAndSku(UUID orderId, String sku);

    List<Reservation> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);
}
