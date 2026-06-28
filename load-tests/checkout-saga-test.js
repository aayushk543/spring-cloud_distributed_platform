import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const ordersPlaced = new Counter('orders_placed');
const sagaCompleted = new Counter('saga_completed');
const sagaFailed = new Counter('saga_failed');
const completionRate = new Rate('saga_completion_rate');

export const options = {
  stages: [
    { duration: '20s', target: 25 },
    { duration: '2m', target: 50 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],
    saga_completion_rate: ['rate>0.5'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCTS = ['PROD-001', 'PROD-002', 'PROD-003', 'PROD-004', 'PROD-005'];

export function setup() {
  http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    username: 'saga_user', email: 'saga@test.com', password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: 'saga_user', password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  return { token: JSON.parse(loginRes.body).token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
  const quantity = Math.floor(Math.random() * 3) + 1;

  // Place an order
  const orderRes = http.post(`${BASE_URL}/orders`, JSON.stringify({
    userId: `user_${__VU}`,
    productId: product,
    quantity: quantity,
    idempotencyKey: `order-${__VU}-${__ITER}-${Date.now()}`,
  }), { headers });

  check(orderRes, { 'order created': (r) => r.status === 201 });

  if (orderRes.status === 201) {
    ordersPlaced.add(1);
    const order = JSON.parse(orderRes.body);

    // Poll for saga completion (max 10 seconds)
    for (let i = 0; i < 5; i++) {
      sleep(2);
      const statusRes = http.get(`${BASE_URL}/orders/${order.id}`, { headers });
      if (statusRes.status === 200) {
        const status = JSON.parse(statusRes.body).status;
        if (status === 'COMPLETED') {
          sagaCompleted.add(1);
          completionRate.add(true);
          return;
        } else if (status === 'FAILED') {
          sagaFailed.add(1);
          completionRate.add(false);
          return;
        }
      }
    }
    completionRate.add(false);
  }
}
