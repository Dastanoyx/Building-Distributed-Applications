package dev.orderflow.notification;

import dev.orderflow.events.Topics;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * What happens when a message cannot be processed (Session 08).
 *
 * <p>Two categories, treated differently:</p>
 * <ul>
 *   <li><b>Retryable</b> — the database is briefly down, a dependency times out. Back off and
 *       try again a few times; it will probably work.</li>
 *   <li><b>Not retryable</b> — the payload cannot be deserialised, or violates a constraint.
 *       Retrying is pointless and blocks the partition for every other message behind it, so
 *       the record goes straight to the dead letter topic.</li>
 * </ul>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        // Same partition in the DLT as in the source topic: ordering per key is preserved
        // even for the failures, which makes a replay predictable.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(Topics.ORDER_EVENTS_DLT, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);          // 1 s, 2 s, 4 s

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // A malformed payload will never become valid: do not waste three attempts on it.
        handler.addNotRetryableExceptions(
                DeserializationException.class,
                IllegalArgumentException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class);

        handler.setRetryListeners((record, exception, attempt) ->
                log.warn("retry {} for {}@{}: {}", attempt, record.topic(), record.offset(),
                        exception.toString()));

        return handler;
    }
}
