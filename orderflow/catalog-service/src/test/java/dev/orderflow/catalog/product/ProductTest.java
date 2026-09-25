package dev.orderflow.catalog.product;

import dev.orderflow.catalog.shared.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Domain tests: no Spring, no database, milliseconds to run (Session 02).
 *
 * <p>These are the tests you write first and run constantly. They cannot catch a wrong SQL
 * query — that is what the integration test is for — but they pin down the business rule.</p>
 */
class ProductTest {

    private Product keyboard() {
        return new Product("KEY-001", "Mechanical keyboard", new BigDecimal("129.00"), 5);
    }

    @Test
    void reserving_within_stock_decreases_it() {
        Product product = keyboard();
        product.reserve(3);
        assertEquals(2, product.getStock());
    }

    @Test
    void reserving_more_than_stock_is_refused_and_leaves_the_stock_untouched() {
        Product product = keyboard();

        InsufficientStockException ex = assertThrows(InsufficientStockException.class,
                () -> product.reserve(6));

        assertEquals(6, ex.getRequested());
        assertEquals(5, ex.getAvailable());
        assertEquals(5, product.getStock(), "a refused reservation must not change the stock");
    }

    @Test
    void releasing_puts_units_back() {
        Product product = keyboard();
        product.reserve(5);
        product.release(2);
        assertEquals(2, product.getStock());
    }

    @ParameterizedTest
    @CsvSource({
            "key-001, Keyboard, 10.00, 1",   // lower-case SKU
            "KEY-001, Keyboard, -1.00, 1",   // negative price
            "KEY-001, Keyboard, 10.00, -5"   // negative stock
    })
    void invalid_values_are_refused_at_construction(String sku, String name, BigDecimal price, int stock) {
        assertThrows(IllegalArgumentException.class, () -> new Product(sku, name, price, stock));
    }
}
