import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const rateLimited = new Rate('rate_limited');
const latency = new Trend('request_latency');

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    rate_limited: ['rate>0.01'], // Expect some rate limiting
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Register a user first (will fail after first run, that's fine)
  const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    username: `loadtest_${__VU}_${__ITER}`,
    email: `loadtest_${__VU}_${__ITER}@test.com`,
    password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  // Login
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: `loadtest_${__VU}_0`,
    password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  // Rapid-fire requests to trigger rate limiting
  for (let i = 0; i < 15; i++) {
    const res = http.get(`${BASE_URL}/auth/validate`, {
      headers: { 'Authorization': 'Bearer invalid-token' },
    });

    latency.add(res.timings.duration);
    rateLimited.add(res.status === 429);

    check(res, {
      'got response': (r) => r.status === 200 || r.status === 401 || r.status === 429,
    });
  }

  sleep(1);
}
