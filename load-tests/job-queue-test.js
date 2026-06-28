import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const jobsCreated = new Counter('jobs_created');
const jobsCompleted = new Counter('jobs_completed');
const jobLatency = new Trend('job_creation_latency');

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    jobs_created: ['count>500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRIORITIES = ['HIGH', 'MEDIUM', 'LOW'];

export function setup() {
  // Register and login to get a token
  http.post(`${BASE_URL}/auth/register`, JSON.stringify({
    username: 'jobtest_user', email: 'jobtest@test.com', password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: 'jobtest_user', password: 'password123',
  }), { headers: { 'Content-Type': 'application/json' } });

  const body = JSON.parse(loginRes.body);
  return { token: body.token };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${data.token}`,
  };

  const priority = PRIORITIES[Math.floor(Math.random() * PRIORITIES.length)];

  // Submit a job
  const createRes = http.post(`${BASE_URL}/jobs`, JSON.stringify({
    name: `load-test-job-${__VU}-${__ITER}`,
    payload: JSON.stringify({ task: 'process_data', batch: __ITER }),
    priority: priority,
    maxRetries: 3,
  }), { headers });

  check(createRes, {
    'job created': (r) => r.status === 201,
  });

  if (createRes.status === 201) {
    jobsCreated.add(1);
    jobLatency.add(createRes.timings.duration);

    const job = JSON.parse(createRes.body);

    // Poll job status
    sleep(3);
    const statusRes = http.get(`${BASE_URL}/jobs/${job.id}`, { headers });
    check(statusRes, {
      'job status retrieved': (r) => r.status === 200,
    });

    if (statusRes.status === 200) {
      const status = JSON.parse(statusRes.body).status;
      if (status === 'COMPLETED') {
        jobsCompleted.add(1);
      }
    }
  }

  sleep(1);
}

export function teardown(data) {
  // Check final stats
  const headers = {
    'Authorization': `Bearer ${data.token}`,
  };
  const statsRes = http.get(`${BASE_URL}/jobs/stats`, { headers });
  console.log(`Job Queue Stats: ${statsRes.body}`);
}
