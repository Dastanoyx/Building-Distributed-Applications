# Session 09 — Sagas, the outbox and eventual consistency

**Goal of the lab:** Make « stock reserved » and « order saved » and « event published » agree, always.

## Code to read

| File | Why |
|---|---|
| `orderflow/order-service/.../outbox/` | the row written in the business transaction, and the drainer with FOR UPDATE SKIP LOCKED |
| `orderflow/order-service/.../saga/OrderSagaOrchestrator.java` | steps forward, compensations backward, and the sweeper for stuck sagas |
| `orderflow/order-service/.../saga/SagaState.java` | the transition table that makes duplicates harmless |
| `orderflow/payment-service/.../PaymentListener.java` | the service that declines, so compensation is real |

## What you build

Replace any direct `kafka.send` with an outbox insert, then implement the saga and trigger the compensation with an order above the payment limit.

## Commands

```bash
docker compose stop kafka
curl -s -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":1}]}' | jq .status
docker compose exec order-db psql -U orders -d orders -c 'select count(*) from outbox where published_at is null;'
docker compose start kafka
```

## It works when

- orders are accepted while Kafka is down and the events arrive after it returns
- a declined payment releases the stock and cancels the order
- a duplicated event produces no second payment command

---

Statements and solutions: `Session_09_Exercises.pdf` and `Session_09_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).
