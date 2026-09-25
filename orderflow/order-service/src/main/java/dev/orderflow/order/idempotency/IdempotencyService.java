package dev.orderflow.order.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Claim, then complete: the two-step protocol that makes POST safe to retry (Session 05).
 *
 * <ol>
 *   <li>{@link #claimOrReplay} tries to insert the key. Winning means « do the work ».
 *       Losing means the work was already done (replay it) or is in flight (ask the client
 *       to retry in a moment).</li>
 *   <li>{@link #complete} stores the outcome so the next duplicate can be answered without
 *       touching the catalog or creating a second order.</li>
 * </ol>
 *
 * <p>Both run in their own transaction ({@code REQUIRES_NEW}): the claim must survive even if
 * the business transaction rolls back, otherwise a failed attempt would free the key and let a
 * duplicate through.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** What the caller should do next. */
    public sealed interface Decision {
        /** Nobody claimed this key: do the work. */
        record Proceed() implements Decision { }

        /** Already completed: return this exact answer again. */
        record Replay(int status, String body) implements Decision { }

        /** Claimed but not finished yet: tell the client to retry shortly (409 + Retry-After). */
        record InProgress() implements Decision { }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision claimOrReplay(String key, Object requestBody) {
        String fingerprint = fingerprintOf(requestBody);

        if (repository.claim(key, fingerprint, Instant.now().plus(RETENTION)) == 1) {
            return new Decision.Proceed();
        }

        IdempotencyRecord existing = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("idempotency key vanished: " + key));

        if (!existing.matches(fingerprint)) {
            // Same key, different payload: the client is reusing keys. Never replay here —
            // that would answer a question nobody asked.
            throw new IdempotencyConflictException(key);
        }
        if (existing.isCompleted()) {
            log.info("duplicate request with key {}: replaying the stored answer", key);
            return new Decision.Replay(existing.getResponseStatus(), existing.getResponseBody());
        }
        return new Decision.InProgress();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String key, int status, Object responseBody) {
        repository.findById(key).ifPresent(record -> {
            try {
                record.complete(status, objectMapper.writeValueAsString(responseBody));
            } catch (Exception e) {
                log.warn("could not serialise the response for key {}", key, e);
            }
        });
    }

    /** Frees a claim whose work failed, so the client may genuinely retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abandon(String key) {
        repository.deleteById(key);
    }

    /**
     * Housekeeping.
     *
     * <p>This job is safe on several replicas even though it has no lock, because the delete is
     * idempotent: three instances deleting the same expired rows is wasteful, not wrong. The
     * saga sweeper, which <b>publishes</b>, is a different story — see Session 13.</p>
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("purged {} expired idempotency keys", deleted);
        }
    }

    private String fingerprintOf(Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        } catch (Exception e) {
            return HexFormat.of().formatHex(String.valueOf(body).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Same key, different body. */
    public static class IdempotencyConflictException extends RuntimeException {
        public IdempotencyConflictException(String key) {
            super("Idempotency-Key %s was already used with a different request body".formatted(key));
        }
    }

    /** Convenience for callers that only need the optional replay. */
    public Optional<Decision.Replay> replayOf(Decision decision) {
        return decision instanceof Decision.Replay replay ? Optional.of(replay) : Optional.empty();
    }
}
