# Session 13 — Coordination: clocks, locks and leader election

**Goal of the lab:** Fix the scheduled job that runs three times when you scale to three replicas.

## Code to read

| File | Why |
|---|---|
| `orderflow/order-service/.../saga/OrderSagaOrchestrator.java` | `sweepStuckSagas` — read the warning in its Javadoc |
| `orderflow/order-service/.../outbox/OutboxRepository.java` | `FOR UPDATE SKIP LOCKED`: replicas cooperating without a lock service |
| `orderflow/order-service/.../idempotency/IdempotencyService.java` | a scheduled job that is safe unlocked, and why |

## What you build

Add ShedLock to the saga sweeper, then implement the same guarantee with a single conditional UPDATE and compare the two.

## Commands

```bash
docker compose up -d --scale order-service=3
docker compose logs -f order-service | grep -i sweeper
```

## It works when

- with three replicas the sweeper runs once per tick
- you can explain why the outbox drainer needs no lock but the sweeper does
- you never order events by wall-clock timestamp

---

Statements and solutions: `Session_13_Exercises.pdf` and `Session_13_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).
