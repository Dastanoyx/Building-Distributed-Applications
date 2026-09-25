package dev.orderflow.order.order.dto;

import dev.orderflow.order.order.Order;
import dev.orderflow.order.order.OrderLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the API returns for an order.
 *
 * <p>{@code enriched} is the honesty flag of the degraded mode (Session 06): when the catalog is
 * unavailable the order is still returned, with SKUs but without product names, and the client
 * is told so instead of being shown a blank field or an error page.</p>
 */
public record OrderResponse(UUID id, String customerId, String status, BigDecimal total,
                            List<LineResponse> lines, boolean enriched, String cancellationReason,
                            Instant createdAt) {

    public record LineResponse(String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal total) {
    }

    /** Plain view: everything the order service knows by itself. */
    public static OrderResponse from(Order order) {
        return build(order, Map.of(), false);
    }

    /** Enriched view: product names fetched from the catalog. */
    public static OrderResponse enriched(Order order, Map<String, String> namesBySku) {
        return build(order, namesBySku, true);
    }

    private static OrderResponse build(Order order, Map<String, String> names, boolean enriched) {
        List<LineResponse> lines = order.getLines().stream()
                .map(line -> new LineResponse(line.getSku(), names.get(line.getSku()),
                        line.getQuantity(), line.getUnitPrice(), line.total()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus().name(),
                order.getTotal(), lines, enriched, order.getCancellationReason(), order.getCreatedAt());
    }
}
