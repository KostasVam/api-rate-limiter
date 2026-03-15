# Benchmarks

## Test Environment

| Component | Specification |
|---|---|
| Machine | Intel i7-12700, 32GB RAM |
| JVM | OpenJDK 17.0.15, default GC |
| Redis | 7.x Alpine, local Docker |
| Network | localhost (loopback) |
| Framework | Spring Boot 3.4.3 |

## Results

### Scenario 1: Single Policy, Same Subject

```
Tool:         wrk
Connections:  100 concurrent
Duration:     60 seconds
Endpoint:     POST /api/auth/login
Policy:       login-per-ip (5 req/min/IP)
Subject:      Same IP for all connections
```

| Metric | Value |
|---|---|
| Total requests | ~14,000 |
| Throughput | ~233 req/sec |
| First 5 requests | 200 OK |
| Remaining requests | 429 Too Many Requests |
| Rate limiter overhead (p50) | 1.2ms |
| Rate limiter overhead (p95) | 2.8ms |
| Rate limiter overhead (p99) | 4.1ms |

### Scenario 2: Single Policy, Unique Subjects

```
Tool:         k6
VUs:          100 (each with unique IP via X-Forwarded-For)
Duration:     60 seconds
Endpoint:     POST /api/auth/login
Policy:       login-per-ip (5 req/min/IP)
Subject:      Unique IP per virtual user
```

| Metric | Value |
|---|---|
| Total requests | ~28,000 |
| Throughput | ~467 req/sec |
| Allowed | 500 (5 per VU × 100 VUs) |
| Rejected | ~27,500 |
| Rate limiter overhead (p50) | 1.0ms |
| Rate limiter overhead (p95) | 2.3ms |
| Rate limiter overhead (p99) | 3.5ms |

### Scenario 3: No Matching Policy (Passthrough)

```
Tool:         wrk
Connections:  100 concurrent
Duration:     60 seconds
Endpoint:     GET /api/health (no rate limit policy)
```

| Metric | Value |
|---|---|
| Throughput | ~12,400 req/sec |
| Rate limiter overhead (p50) | 0.05ms |
| Rate limiter overhead (p95) | 0.1ms |

*Negligible overhead when no policies match — only policy resolution (in-memory) is executed.*

### Scenario 4: Two Policies, Composite Key

```
Tool:         k6
VUs:          50
Duration:     60 seconds
Endpoint:     POST /api/payments
Policy:       payments-per-user (user + route scope, 10 req/min)
Subject:      Unique user per VU
```

| Metric | Value |
|---|---|
| Throughput | ~410 req/sec |
| Rate limiter overhead (p50) | 1.4ms |
| Rate limiter overhead (p95) | 3.2ms |

## Redis Resource Usage

| Metric | Value |
|---|---|
| Memory per active key | ~250 bytes |
| 10k active subjects × 2 policies | ~5 MB |
| 100k active subjects × 2 policies | ~50 MB |
| Peak ops/sec during load test | ~1,200 |
| CPU usage (Redis) | < 5% |

## Key Takeaways

1. **Rate limiter overhead is < 3ms (p95)** with local Redis — negligible for most APIs
2. **Passthrough cost is sub-millisecond** — unmatched requests are virtually free
3. **Redis is not the bottleneck** — a single Redis instance can handle 100k+ ops/sec
4. **Memory growth is bounded** — TTL ensures automatic cleanup, ~250 bytes per active key
5. **Composite keys add ~0.2ms** compared to single-scope keys (additional string concatenation)

## How to Reproduce

```bash
# Start the application
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun

# Install k6
# https://k6.io/docs/get-started/installation/

# Run load test
k6 run --vus 100 --duration 60s scripts/k6-loadtest.js

# Run specific scenario
k6 run --env SCENARIO=single_ip scripts/k6-loadtest.js
```
