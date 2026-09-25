package dev.orderflow.catalog.product.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable pagination envelope.
 *
 * <p>Spring's own {@code Page} serialises with an unstable structure across versions, so the
 * API exposes this small record instead. Collections are <b>always</b> paginated: an endpoint
 * that returns everything works with fifty rows in development and takes the service down
 * with five hundred thousand in production (Session 02).</p>
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
