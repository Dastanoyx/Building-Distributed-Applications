package dev.orderflow.catalog.product;

import dev.orderflow.catalog.product.dto.PageResponse;
import dev.orderflow.catalog.product.dto.ProductRequest;
import dev.orderflow.catalog.product.dto.ProductResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;

/**
 * HTTP entry point for products.
 *
 * <p>The controller only translates: HTTP in, DTO out, correct status code. No business rule
 * lives here — otherwise it could not be reused by the Kafka consumer or tested without
 * MockMvc (Session 02).</p>
 *
 * <p>Status codes are deliberate: <b>201 + Location</b> on creation, <b>204</b> on deletion,
 * <b>404</b> for an unknown SKU, <b>409</b> for a conflict. A client must be able to tell
 * « created », « nothing to do » and « you sent something wrong » apart.</p>
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** GET /api/products?q=key&minPrice=10&maxPrice=200&inStockOnly=true&page=0&size=20&sort=price */
    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStockOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "name") String sort) {
        return service.search(q, minPrice, maxPrice, inStockOnly, page, size, sort);
    }

    /** GET /api/products/{sku} — 404 when the product does not exist. */
    @GetMapping("/{sku}")
    public ProductResponse bySku(@PathVariable String sku) {
        return ProductResponse.from(service.requireBySku(sku));
    }

    /** POST /api/products — 201 with a Location header pointing at the new resource. */
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product created = service.create(request.sku(), request.name(), request.price(), request.stock());
        return ResponseEntity
                .created(URI.create("/api/products/" + created.getSku()))
                .body(ProductResponse.from(created));
    }

    /** DELETE /api/products/{sku} — 204, and safe to call twice. */
    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String sku) {
        service.delete(sku);
    }
}
