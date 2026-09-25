// ---------------------------------------------------------------------------
// Session 12 — load test.
//
//   docker run --rm -i --network host -v "$PWD/load:/load" grafana/k6 run /load/place-order.js
//   (or install k6 locally and run:  k6 run load/place-order.js)
//
// Measure BEFORE tuning, change ONE thing, measure again. Keep the Grafana dashboard open
// while it runs: the four golden signals tell you where the limit actually is.
// ---------------------------------------------------------------------------
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // ramp up
    { duration: '2m',  target: 20 },   // steady state: this is where the numbers come from
    { duration: '30s', target: 100 },  // spike: does it degrade or collapse?
    { duration: '1m',  target: 0 },    // ramp down
  ],
  // The run FAILS if these are not met, so it can gate a pipeline.
  thresholds: {
    'http_req_duration{scenario:default}': ['p(95)<400'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Browsing is read-heavy and dominates real traffic.
  const list = http.get(`${BASE}/api/products?size=20`);
  check(list, { 'catalogue 200': (r) => r.status === 200 });

  // One order per iteration, with a unique idempotency key (Session 05).
  const payload = JSON.stringify({
    customerId: `c-${__VU}`,
    lines: [{ sku: 'KEY-001', quantity: 1 }],
  });
  const placed = http.post(`${BASE}/api/orders`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}`,
    },
  });
  check(placed, {
    'order accepted or honestly refused': (r) => [201, 409, 503].includes(r.status),
  });

  sleep(1);
}
