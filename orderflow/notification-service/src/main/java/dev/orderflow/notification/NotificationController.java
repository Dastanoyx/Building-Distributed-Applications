package dev.orderflow.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A read-only window on what this service produced.
 *
 * <p>Useful during the demo: place an order and show the notification appearing a few
 * milliseconds later — the visible face of eventual consistency (Session 09).</p>
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final NotificationRepository repository;

    public NotificationController(NotificationService service, NotificationRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /** GET /api/notifications?customerId=c-1 */
    @GetMapping
    public Page<NotificationResponse> byCustomer(@RequestParam String customerId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return service.byCustomer(customerId, PageRequest.of(page, Math.min(size, 100)))
                .map(NotificationResponse::from);
    }

    /** GET /api/notifications/order/{orderId} — everything said about one order. */
    @GetMapping("/order/{orderId}")
    public List<NotificationResponse> byOrder(@PathVariable UUID orderId) {
        return repository.findByOrderId(orderId).stream().map(NotificationResponse::from).toList();
    }

    public record NotificationResponse(UUID id, UUID orderId, String customerId, String type,
                                       String message, Instant createdAt) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getOrderId(), n.getCustomerId(),
                    n.getType(), n.getMessage(), n.getCreatedAt());
        }
    }
}
