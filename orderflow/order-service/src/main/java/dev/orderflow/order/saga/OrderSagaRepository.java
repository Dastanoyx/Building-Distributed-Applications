package dev.orderflow.order.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence for sagas. */
public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    /** Sagas that have not moved for a while: either a lost message, or a dead instance. */
    List<OrderSaga> findByStateInAndLastTransitionAtBefore(Collection<SagaState> states, Instant threshold);

    long countByState(SagaState state);
}
