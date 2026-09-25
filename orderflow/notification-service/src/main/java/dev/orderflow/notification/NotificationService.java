package dev.orderflow.notification;

import dev.orderflow.events.OrderEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns order facts into notifications, exactly once per fact (Session 08).
 *
 * <p>Every handler follows the same three lines:</p>
 * <ol>
 *   <li>claim the event id — if the insert affects no row, this is a redelivery, return;</li>
 *   <li>do the work;</li>
 *   <li>commit both together.</li>
 * </ol>
 *
 * <p>The claim and the work share one {@code @Transactional} method on purpose. Splitting them
 * into two transactions would allow « marked as processed, then crashed » — and the real work
 * would never happen.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;
    private final MeterRegistry meters;

    public NotificationService(NotificationRepository notifications, ProcessedEventRepository processed,
                               MeterRegistry meters) {
        this.notifications = notifications;
        this.processed = processed;
        this.meters = meters;
    }

    @Transactional
    public void handle(OrderEvent event) {
        if (processed.insertIfAbsent(event.eventId()) == 0) {
            meters.counter("orderflow.events.duplicates").increment();
            log.debug("duplicate event {} ignored", event.eventId());
            return;
        }

        Notification notification = switch (event) {
            case OrderEvent.OrderPlaced placed -> new Notification(placed.orderId(), placed.customerId(),
                    "ORDER_PLACED",
                    "We received your order of %s. We will confirm it shortly.".formatted(placed.total()));

            case OrderEvent.OrderConfirmed confirmed -> new Notification(confirmed.orderId(),
                    confirmed.customerId(), "ORDER_CONFIRMED",
                    "Your order is confirmed. Total charged: %s.".formatted(confirmed.total()));

            case OrderEvent.OrderCancelled cancelled -> new Notification(cancelled.orderId(),
                    cancelled.customerId(), "ORDER_CANCELLED",
                    "Your order was cancelled: %s. Nothing was charged.".formatted(cancelled.reason()));
        };

        notifications.save(notification);
        meters.counter("orderflow.notifications", "type", notification.getType()).increment();
        log.info("stored {} for order {}", notification.getType(), notification.getOrderId());
    }

    @Transactional(readOnly = true)
    public Page<Notification> byCustomer(String customerId, Pageable pageable) {
        return notifications.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public long countForOrder(UUID orderId) {
        return notifications.countByOrderId(orderId);
    }
}
