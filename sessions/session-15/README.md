# Session 15 — Testing strategy, delivery and defence

**Goal of the lab:** Prove the system behaves, including when it breaks.

## Code to read

| File | Why |
|---|---|
| `orderflow/catalog-service/src/test/.../ReservationServiceIT.java` | integration with a real database |
| `orderflow/order-service/src/test/.../PlaceOrderIT.java` | the four scenarios: happy path, idempotency, business refusal, outage |
| `orderflow/scripts/demo.sh` | the seven-minute demo, scripted |
| `orderflow/load/place-order.js` | the numbers you publish in docs/quality.md |

## What you build

Add a contract test between order and catalog, fault-injection tests, and complete the documentation set.

## Commands

```bash
mvn verify
./scripts/demo.sh
```

## It works when

- a clean clone starts with one command by following your README only
- killing any service produces a defined, demonstrated behaviour
- you can explain any line of your code and what you would change next

> **Final delivery** — tag `v1.0`, green CI, documentation, and the live demo including a failure and its recovery.

---

Statements and solutions: `Session_15_Exercises.pdf` and `Session_15_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).
