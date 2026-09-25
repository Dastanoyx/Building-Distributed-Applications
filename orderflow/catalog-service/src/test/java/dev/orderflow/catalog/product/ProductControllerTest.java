package dev.orderflow.catalog.product;

import dev.orderflow.catalog.shared.DuplicateSkuException;
import dev.orderflow.catalog.shared.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests (Session 02).
 *
 * <p>{@code @WebMvcTest} loads only the controller, the JSON converters, bean validation and
 * the exception handler, with the service mocked. The whole class runs in a couple of seconds
 * and tells you exactly which layer is broken when it fails.</p>
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    /** Spring Boot 3.4+ renames this to @MockitoBean; @MockBean still works on 3.3. */
    @MockBean
    private ProductService service;

    @Test
    void creating_a_product_returns_201_and_a_location_header() throws Exception {
        when(service.create(eq("KEY-001"), any(), any(), anyInt()))
                .thenReturn(new Product("KEY-001", "Mechanical keyboard", new BigDecimal("129.00"), 5));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sku":"KEY-001","name":"Mechanical keyboard","price":129.00,"stock":5}
                                 """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/KEY-001"))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    void an_invalid_payload_never_reaches_the_service() throws Exception {
        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sku":"bad","name":"","price":0,"stock":-1}
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());

        // Validation happens before the application layer: this is the assertion that proves it.
        verifyNoInteractions(service);
    }

    @Test
    void an_unknown_sku_returns_a_404_problem_document_with_a_correlation_id() throws Exception {
        when(service.requireBySku("ZZZ-999")).thenThrow(new NotFoundException("Product ZZZ-999 not found"));

        mvc.perform(get("/api/products/ZZZ-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void a_duplicate_sku_returns_409_with_the_offending_sku() throws Exception {
        when(service.create(any(), any(), any(), anyInt())).thenThrow(new DuplicateSkuException("KEY-001"));

        mvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"sku":"KEY-001","name":"Mechanical keyboard","price":129.00,"stock":5}
                                 """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sku").value("KEY-001"));
    }
}
