package dev.orderflow.order.shared;

import dev.orderflow.order.client.CatalogExceptions;
import dev.orderflow.order.idempotency.IdempotencyService;
import dev.orderflow.order.order.IllegalOrderTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Translating failures into HTTP, deliberately (Sessions 02, 05 and 06).
 *
 * <p>The mapping is a design decision, not a detail:</p>
 * <ul>
 *   <li>an unknown SKU is the <b>caller's</b> mistake → 400;</li>
 *   <li>not enough stock is a valid request the state refuses → 409;</li>
 *   <li>the catalog being down is <b>our</b> problem → 503 with {@code Retry-After}, never 400.</li>
 * </ul>
 *
 * <p>Getting this wrong makes every dashboard and alert downstream meaningless: a 4xx tells the
 * client to change something, a 5xx tells them to come back later.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
    }

    /** A SKU that does not exist: the order itself is wrong. */
    @ExceptionHandler(CatalogExceptions.ProductNotFound.class)
    public ProblemDetail onUnknownSku(CatalogExceptions.ProductNotFound ex, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Unknown SKU",
                "The order references a product that does not exist", request);
        detail.setProperty("sku", ex.getSku());
        return detail;
    }

    /** A business refusal, with the numbers the client needs to fix the order. */
    @ExceptionHandler(CatalogExceptions.StockUnavailable.class)
    public ProblemDetail onStock(CatalogExceptions.StockUnavailable ex, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Insufficient stock",
                "Not enough stock to accept this order", request);
        detail.setProperty("sku", ex.getSku());
        detail.setProperty("requested", ex.getRequested());
        detail.setProperty("available", ex.getAvailable());
        return detail;
    }

    /**
     * The dependency is unavailable.
     *
     * <p>503 with {@code Retry-After} is the honest answer: the request was fine, we cannot serve
     * it right now, come back in five seconds. Inventing a reservation here would oversell
     * (Session 06).</p>
     */
    @ExceptionHandler(CatalogExceptions.CatalogUnavailable.class)
    public ResponseEntity<ProblemDetail> onCatalogDown(CatalogExceptions.CatalogUnavailable ex,
                                                       HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable",
                "Orders cannot be accepted right now because stock cannot be verified", request);
        detail.setProperty("retryable", true);
        log.warn("catalog unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(detail);
    }

    /** Same idempotency key, different body: the client is reusing keys. */
    @ExceptionHandler(IdempotencyService.IdempotencyConflictException.class)
    public ProblemDetail onIdempotencyConflict(IdempotencyService.IdempotencyConflictException ex,
                                               HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Idempotency key conflict", ex.getMessage(), request);
    }

    /** A duplicate or out-of-order event tried an impossible transition. */
    @ExceptionHandler(IllegalOrderTransitionException.class)
    public ProblemDetail onIllegalTransition(IllegalOrderTransitionException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Illegal state transition", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid", request);
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(),
                    Optional.ofNullable(fieldError.getDefaultMessage()).orElse("invalid"));
        }
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error",
                "The request could not be processed", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detailText,
                                  HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, detailText);
        detail.setTitle(title);
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("correlationId",
                Optional.ofNullable(MDC.get(CorrelationIdFilter.MDC_KEY)).orElse("unknown"));
        detail.setProperty("timestamp", Instant.now().toString());
        return detail;
    }
}
