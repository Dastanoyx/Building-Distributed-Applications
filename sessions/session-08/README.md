# Session 08 — Kafka: events, partitions and idempotent consumers

**Goal of the lab:** Add the notification service and make it survive duplicate deliveries.

## Code to read

| File | Why |
|---|---|
| `orderflow/common-events/.../OrderEvent.java` | sealed events, past tense, with their own event id |
| `orderflow/order-service/.../config/KafkaConfig.java` | 3 partitions, retention as a contract |
| `orderflow/notification-service/.../NotificationService.java` | claim the event id and do the work in ONE transaction |
| `orderflow/notification-service/.../KafkaErrorHandlingConfig.java` | retry what can succeed, dead-letter what cannot |

## What you build

Build the notification service end to end, then publish the same event twice and prove only one notification exists.

## Commands

```bash
docker compose exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-events --from-beginning --property print.key=true --property print.partition=true
docker compose up -d --scale notification-service=2
docker compose logs -f notification-service | grep -i 'partitions assigned'
```

## It works when

- all events of one order land in the same partition
- a fourth consumer on three partitions is idle
- a malformed message goes to the DLT without blocking the partition

---

Statements and solutions: `Session_08_Exercises.pdf` and `Session_08_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).
