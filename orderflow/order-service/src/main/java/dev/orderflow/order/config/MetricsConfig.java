package dev.orderflow.order.config;

import dev.orderflow.order.order.OrderRepository;
import dev.orderflow.order.order.OrderStatus;
import dev.orderflow.order.outbox.OutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Business metrics — the ones nobody else can infer from HTTP statistics (Session 11).
 *
 * <p>« Events waiting in the outbox » is the single most useful gauge in this service: it is
 * flat at zero in normal operation and climbs the moment Kafka is unreachable, which makes it
 * an excellent alert.</p>
 *
 * <p>Note what is <b>not</b> tagged: no order id, no customer id. A tag with unbounded values
 * creates one time series per value and takes Prometheus down (Session 11).</p>
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Gauge pendingOrdersGauge(MeterRegistry registry, OrderRepository orders) {
        return Gauge.builder("orderflow.orders.pending", orders,
                        repository -> repository.countByStatus(OrderStatus.PENDING))
                .description("Orders waiting for payment confirmation")
                .register(registry);
    }

    @Bean
    public Gauge outboxPendingGauge(MeterRegistry registry, OutboxRepository outbox) {
        return Gauge.builder("orderflow.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("Events written but not yet published to Kafka")
                .register(registry);
    }
}
