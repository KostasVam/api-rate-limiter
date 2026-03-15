import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics
const rateLimited = new Counter('rate_limited_responses');
const allowed = new Counter('allowed_responses');
const rateLimitRemaining = new Trend('rate_limit_remaining');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ---------------------------------------------------------------------------
// Scenarios
// ---------------------------------------------------------------------------
// Run a specific scenario:   k6 run --env SCENARIO=single_ip scripts/k6-loadtest.js
// Run all scenarios:         k6 run scripts/k6-loadtest.js
// ---------------------------------------------------------------------------

const SCENARIOS = {
  // Scenario 1: Single IP hammering login endpoint
  // Expected: first 5 requests allowed, rest get 429
  single_ip: {
    executor: 'constant-vus',
    vus: 10,
    duration: '30s',
    exec: 'singleIpLogin',
  },

  // Scenario 2: Many unique IPs hitting login
  // Expected: each IP gets its own 5 req/min quota
  many_ips: {
    executor: 'constant-vus',
    vus: 50,
    duration: '30s',
    exec: 'manyIpsLogin',
  },

  // Scenario 3: Sustained load just under limit
  // Expected: zero 429 responses
  under_limit: {
    executor: 'constant-arrival-rate',
    rate: 4,              // 4 req/min (under 5 limit)
    timeUnit: '1m',
    duration: '2m',
    preAllocatedVUs: 2,
    exec: 'underLimitLogin',
  },
};

const selectedScenario = __ENV.SCENARIO;

export const options = {
  scenarios: selectedScenario
    ? { [selectedScenario]: SCENARIOS[selectedScenario] }
    : SCENARIOS,
  thresholds: {
    http_req_duration: ['p(95)<100'],  // 95% of requests under 100ms
  },
};

// ---------------------------------------------------------------------------
// Scenario functions
// ---------------------------------------------------------------------------

// Scenario 1: All VUs share the same IP → rapid limit exhaustion
export function singleIpLogin() {
  const res = http.post(`${BASE_URL}/api/auth/login`, null, {
    headers: { 'X-Forwarded-For': '10.0.0.100' },
  });

  checkResponse(res);
  sleep(0.1);
}

// Scenario 2: Each VU gets a unique IP
export function manyIpsLogin() {
  const ip = `10.0.${Math.floor(__VU / 256)}.${__VU % 256}`;

  const res = http.post(`${BASE_URL}/api/auth/login`, null, {
    headers: { 'X-Forwarded-For': ip },
  });

  checkResponse(res);
  sleep(0.5);
}

// Scenario 3: Single IP, rate controlled to stay under limit
export function underLimitLogin() {
  const res = http.post(`${BASE_URL}/api/auth/login`, null, {
    headers: { 'X-Forwarded-For': '10.0.0.200' },
  });

  const ok = check(res, {
    'under-limit request allowed': (r) => r.status === 200,
  });

  if (!ok) {
    console.error(`Unexpected 429 at iteration ${__ITER}`);
  }

  trackMetrics(res);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function checkResponse(res) {
  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'has rate limit header': (r) => r.headers['X-Ratelimit-Limit'] !== undefined,
  });

  trackMetrics(res);
}

function trackMetrics(res) {
  if (res.status === 429) {
    rateLimited.add(1);
  } else {
    allowed.add(1);
  }

  const remaining = res.headers['X-Ratelimit-Remaining'];
  if (remaining !== undefined) {
    rateLimitRemaining.add(parseInt(remaining, 10));
  }
}
