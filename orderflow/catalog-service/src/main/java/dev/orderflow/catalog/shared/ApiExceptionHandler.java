package dev.orderflow.catalog.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
 * One error shape for the whole platform: RFC 9457 {@code application/problem+json}
 * (Session 02).
 *
 * <p>Clients parse one structure, always. Internals never leak: the stack trace goes to the
 * log together with the correlation id, and the client receives that id so support can find
 * the exact log line without ever seeing the exception.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 404 — the resource does not exist. */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
    }

    /** 409 — the request is valid but conflicts with the current state. */
    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail onDuplicate(DuplicateSkuException ex, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Duplicate SKU", ex.getMessage(), request);
        detail.setProperty("sku", ex.getSku());
        return detail;
    }

    /** 409 — a business refusal, with the numbers the caller needs to react. */
    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail onInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        ProblemDetail detail = problem(HttpStatus.CONFLICT, "Insufficient stock", ex.getMessage(), request);
        detail.setProperty("sku", ex.getSku());
        detail.setProperty("requested", ex.getRequested());
        detail.setProperty("available", ex.getAvailable());
        return detail;
    }

    /** 400 — bean validation failed; every offending field is reported at once. */
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

    /** 400 — a bad argument that bean validation could not catch. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), request);
    }

    /** 500 — last resort. Full detail to the log, nothing but an id to the caller. */
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
