package dev.orderflow.order.config;

import dev.orderflow.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Duration;

/**
 * Topics created at startup (Session 08).
 *
 * <p>Three partitions, so up to three consumers of a group can work in parallel while every
 * event of one order still lands in the same partition and stays ordered. Replication factor 1
 * only because this is a single-broker development stack — production uses 3 with
 * {@code min.insync.replicas=2}.</p>
 *
 * <p>Retention is a contract: a consumer that is down longer than seven days loses data.</p>
 */
@Configuration
public class KafkaConfig {

    private static final String RETENTION_MS = String.valueOf(Duration.ofDays(7).toMillis());

    @Bean
    public NewTopic orderEvents() {
        return TopicBuilder.name(Topics.ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .config("retention.ms", RETENTION_MS)
                .build();
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name(Topics.ORDER_EVENTS_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCommands() {
        return TopicBuilder.name(Topics.PAYMENT_COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEvents() {
        return TopicBuilder.name(Topics.PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }
}
