# Performance Characteristics

## Latency Model

Each rate-limited request adds one synchronous Redis round trip to the request path.

### Per-Request Overhead

| Operation | Expected Latency |
|---|---|
| Policy resolution (in-memory) | < 0.1ms |
| Subject extraction (in-memory) | < 0.1ms |
| Redis Lua script execution | 0.5 - 2ms (local network) |
| Header serialization | < 0.1ms |
| **Total overhead** | **~1 - 3ms per request** |

### Latency Targets

| Percentile | Target (local Redis) | Target (remote Redis) |
|---|---|---|
| p50 | < 1ms | < 5ms |
| p95 | < 3ms | < 10ms |
| p99 | < 5ms | < 20ms |

Measured via `rate_limiter_eval_duration` histogram.

## Throughput

### Bottleneck Analysis

The primary bottleneck is Redis round-trip time, not CPU.

| Component | Throughput Limit |
|---|---|
| Redis (single instance) | ~100k ops/sec |
| Lettuce connection pool | ~50k req/sec per connection |
| Policy resolution | Not a bottleneck (CPU-bound, microseconds) |

### Expected Throughput

| Scenario | Expected |
|---|---|
| Single policy, single subject scope | ~30k req/sec |
| Two policies per request | ~15k req/sec |
| Composite key (2 scopes) | ~25k req/sec |

*Estimates based on single Redis instance with local network. Actual numbers depend on hardware, network, and Redis configuration.*

## Memory Model

### Redis Memory

Active keys at any point in time:

```
active_keys ≈ active_subjects × matching_policies × 1 (current window only)
```

Each key consumes approximately:
- Key: ~80-150 bytes (e.g., `rl:payments-per-user:user:123|route:POST:/api/payments:28876925`)
- Value: 8 bytes (integer counter)
- Redis overhead: ~80 bytes per key

**Example:** 10,000 active users × 2 policies = 20,000 keys × ~250 bytes ≈ **5 MB**

Keys are automatically evicted by TTL (window_seconds + 5s buffer).

### JVM Memory

- Policy list: negligible (loaded once at startup)
- Metrics cache: O(policies × routes) `Counter` instances
- In-memory backend: O(active_keys) `AtomicInteger` entries with scheduled cleanup

## Load Testing Plan

### Tools

- **k6** — scriptable, supports HTTP/1.1 and HTTP/2
- **wrk** — low-overhead, high-concurrency benchmarking
- **Gatling** — scenario-based, good for complex flows

### Test Scenarios

#### Scenario 1: Single Subject High Concurrency
```
100 concurrent connections
same IP
POST /api/auth/login
duration: 60s
expected: first 5 succeed, rest get 429
```

#### Scenario 2: Many Subjects
```
100 concurrent connections
unique IP per connection
POST /api/auth/login
duration: 60s
expected: each IP gets 5 req/min, all independent
```

#### Scenario 3: Sustained Load at Limit Boundary
```
50 concurrent connections
same user
POST /api/payments
rate: exactly 10 req/min
duration: 5min
expected: zero 429 responses, remaining hovers near 0
```

#### Scenario 4: Redis Latency Impact
```
inject Redis latency via tc or toxiproxy
measure: overall request latency increase
measure: fail-open activation time
```

### Metrics to Collect

- `rate_limiter_eval_duration` — p50, p95, p99
- `rate_limiter_requests_total` — throughput
- `rate_limiter_rejected_total` — rejection rate
- `rate_limiter_errors_total` — backend errors
- Redis: `INFO commandstats`, memory usage, connected clients
- JVM: heap usage, GC pauses
