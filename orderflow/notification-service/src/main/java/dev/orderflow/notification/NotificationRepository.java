package dev.orderflow.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Persistence for notifications. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    List<Notification> findByOrderId(UUID orderId);

    long countByOrderId(UUID orderId);
}
