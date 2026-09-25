#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# The seven-minute demo, as a script (Session 15).
# Run it after `docker compose up -d --build` has settled.
# ---------------------------------------------------------------------------
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
key() { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen; }

say() { printf '\n\033[1;36m== %s\033[0m\n' "$1"; }

say "1. The catalogue is up and seeded"
curl -s "$BASE/api/products?size=3" | head -c 400; echo

say "2. Place an order (201) — stock goes down, an event is queued"
ORDER=$(curl -s -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(key)" \
  -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":2}]}')
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

say "3. The same request twice with ONE key creates ONE order (Session 05)"
K=$(key)
for i in 1 2; do
  curl -s -o /dev/null -w "attempt $i -> %{http_code}\n" -X POST "$BASE/api/orders" \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $K" \
    -d '{"customerId":"c-1","lines":[{"sku":"MOU-002","quantity":1}]}'
done

say "4. The notification arrived through Kafka (eventual consistency, Session 09)"
sleep 2
curl -s "$BASE/api/notifications/order/$ORDER_ID"; echo

say "5. Compensation: an order above the payment limit is cancelled and the stock returns"
curl -s -X POST "$BASE/api/orders" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(key)" \
  -d '{"customerId":"c-1","lines":[{"sku":"SCR-004","quantity":2}]}' | head -c 300; echo
sleep 4
curl -s "$BASE/api/products/SCR-004"; echo

say "6. Kill the catalogue: reads degrade, writes refuse honestly (Session 06)"
docker compose stop catalog-service >/dev/null
echo "GET an existing order:"; curl -s "$BASE/api/orders/$ORDER_ID" | head -c 200; echo
echo "POST a new order:"; curl -s -i -X POST "$BASE/api/orders" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(key)" \
  -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":1}]}' | head -8
docker compose start catalog-service >/dev/null
echo "catalogue restarted — the system recovers on its own"

say "Done. Dashboards: Grafana http://localhost:3000 · Traces: Jaeger http://localhost:16686"
