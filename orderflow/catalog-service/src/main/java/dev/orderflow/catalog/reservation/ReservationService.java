package dev.orderflow.catalog.reservation;

import dev.orderflow.catalog.product.Product;
import dev.orderflow.catalog.product.ProductRepository;
import dev.orderflow.catalog.reservation.dto.ReservationRequest;
import dev.orderflow.catalog.reservation.dto.ReservationResponse;
import dev.orderflow.catalog.shared.InsufficientStockException;
import dev.orderflow.catalog.shared.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holding and releasing stock — the only place in the system where stock goes down.
 *
 * <p>Three properties are enforced here, and each one is a lesson from a different session:</p>
 * <ul>
 *   <li><b>No overselling</b> (Session 03): the decision and the write are a single
 *       {@code UPDATE ... WHERE stock >= :quantity}, so two concurrent requests cannot both
 *       pass the test.</li>
 *   <li><b>All or nothing</b> (Session 02): the whole method runs in one transaction, so a
 *       request with five lines never leaves three of them reserved.</li>
 *   <li><b>Idempotent</b> (Session 05): the same {@code orderId} twice returns the first
 *       outcome instead of reserving twice, which is what makes the caller's retry safe.</li>
 * </ul>
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;

    public ReservationService(ProductRepository products, ReservationRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    /**
     * Holds stock for every line of an order.
     *
     * @throws NotFoundException           if a SKU does not exist — the caller sent a bad order
     * @throws InsufficientStockException  if a line cannot be satisfied; nothing is reserved,
     *                                     because the transaction rolls the earlier lines back
     */
    @Transactional
    public ReservationResponse reserve(ReservationRequest request) {
        UUID orderId = request.orderId();

        // Idempotency: this order was already processed, so replay instead of reserving again.
        if (reservations.existsByOrderId(orderId)) {
            BigDecimal total = priceOf(reservations.findByOrderId(orderId));
            log.info("reservation for order {} already exists, replaying the first answer", orderId);
            return ReservationResponse.alreadyHeld(orderId, total);
        }

        List<Reservation> created = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (ReservationRequest.Line line : request.lines()) {
            Product product = products.findBySku(line.sku())
                    .orElseThrow(() -> new NotFoundException("Product %s not found".formatted(line.sku())));

            // The atomic decision. 0 rows updated means "not enough stock", and because we are
            // inside a transaction, any line reserved before this one is rolled back.
            int updated = products.tryReserve(line.sku(), line.quantity());
            if (updated == 0) {
                throw new InsufficientStockException(line.sku(), line.quantity(), product.getStock());
            }

            created.add(reservations.save(new Reservation(orderId, line.sku(), line.quantity())));
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }

        log.info("reserved {} line(s) for order {}, total {}", created.size(), orderId, total);
        return ReservationResponse.held(orderId, total);
    }

    /**
     * Releases every reservation of an order — the compensating action of the saga (Session 09).
     *
     * <p>Safe to call any number of times: a reservation that is already {@code RELEASED} is
     * skipped, so a redelivered compensation command cannot inflate the stock.</p>
     */
    @Transactional
    public void release(UUID orderId) {
        List<Reservation> held = reservations.findByOrderId(orderId);
        if (held.isEmpty()) {
            log.info("nothing to release for order {} (already released, or never reserved)", orderId);
            return;
        }
        for (Reservation reservation : held) {
            if (reservation.release()) {
                products.release(reservation.getSku(), reservation.getQuantity());
                log.info("released {} x {} for order {}",
                        reservation.getQuantity(), reservation.getSku(), orderId);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Reservation> byOrder(UUID orderId) {
        return reservations.findByOrderId(orderId);
    }

    private BigDecimal priceOf(List<Reservation> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (Reservation line : lines) {
            BigDecimal price = products.findBySku(line.getSku())
                    .map(Product::getPrice)
                    .orElse(BigDecimal.ZERO);
            total = total.add(price.multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        return total;
    }
}
