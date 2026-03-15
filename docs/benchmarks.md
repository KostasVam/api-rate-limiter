# Benchmarks

## JMH Microbenchmarks

Measured with [JMH](https://openjdk.org/projects/code-tools/jmh/) (Java Microbenchmark Harness) using the in-memory backend to isolate algorithm and framework overhead from Redis network latency.

Run with: `./gradlew jmh`

### Algorithm Throughput

| Benchmark | Throughput (ops/μs) | Notes |
|---|---|---|
| Fixed Window (same key) | 12.5 | Baseline — AtomicInteger increment |
| Sliding Window (same key) | 25.9 | One increment + one read |
| Token Bucket (same key) | 25.2 | Refill + consume, per-bucket sync |
| Fixed Window (unique keys) | 1.3 | ConcurrentHashMap allocation overhead |
| Sliding Window (unique keys) | 1.4 | Similar allocation profile |
| Token Bucket (unique keys) | 2.6 | Hash map + bucket creation |

**Takeaway:** All algorithms handle **millions of operations per second** in-process. The bottleneck in production is always Redis network latency (~1ms), not algorithm computation.

### Policy Resolution

| Benchmark | Throughput (ops/μs) | Latency |
|---|---|---|
| 5 policies, matching request | 0.61 | ~1.6 μs |
| 20 policies, matching request | 0.17 | ~5.9 μs |
| 100 policies, matching request | 0.04 | ~26 μs |
| 100 policies, no match | 0.11 | ~9 μs |

**Takeaway:** Even with 100 policies, resolution takes ~26μs — negligible. Scales linearly with policy count. Non-matching requests are faster (no sorting needed).

### Key Building

| Benchmark | Throughput (ops/μs) | Latency |
|---|---|---|
| Single subject (IP only) | 13.2 | ~76 ns |
| Triple subject (IP + user + route) | 4.0 | ~250 ns |

**Takeaway:** Key construction is sub-microsecond. Adding more subjects has linear cost but remains negligible.

## HTTP Load Tests

Measured end-to-end including Spring Boot, rate limiter filter, and Redis round-trip.

### Test Environment

| Component | Specification |
|---|---|
| JVM | OpenJDK 17.0.15, default GC |
| Redis | 7.x Alpine, local Docker |
| Network | localhost (loopback) |
| Framework | Spring Boot 3.4.3 |

### Scenario 1: Single Policy, Same Subject

```
Tool:         k6
Connections:  100 concurrent
Endpoint:     POST /api/auth/login
Policy:       login-per-ip (5 req/min/IP)
Subject:      Same IP for all connections
```

| Metric | Value |
|---|---|
| Rate limiter overhead (p50) | 1.2ms |
| Rate limiter overhead (p95) | 2.8ms |
| Rate limiter overhead (p99) | 4.1ms |

### Scenario 2: Single Policy, Unique Subjects

```
Tool:         k6
VUs:          100 (each with unique IP)
Endpoint:     POST /api/auth/login
Policy:       login-per-ip (5 req/min/IP)
```

| Metric | Value |
|---|---|
| Rate limiter overhead (p50) | 1.0ms |
| Rate limiter overhead (p95) | 2.3ms |
| Rate limiter overhead (p99) | 3.5ms |

### Scenario 3: No Matching Policy (Passthrough)

```
Endpoint:     GET /api/health (excluded path)
```

| Metric | Value |
|---|---|
| Rate limiter overhead (p50) | 0.05ms |
| Rate limiter overhead (p95) | 0.1ms |

*Excluded paths bypass the engine entirely — zero Redis calls.*

## Summary

| Component | Cost | Bottleneck? |
|---|---|---|
| Algorithm computation | < 1μs | No |
| Policy resolution (20 policies) | ~6μs | No |
| Key building (triple subject) | ~250ns | No |
| Redis round-trip (local) | ~1-3ms | **Yes** |
| Total filter overhead (p95) | ~3ms | Acceptable |

**The rate limiter adds ~3ms (p95) overhead with local Redis, dominated entirely by the Redis round-trip. In-process computation is negligible.**

## How to Reproduce

```bash
# JMH microbenchmarks
./gradlew jmh

# HTTP load tests
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun

# k6
k6 run --vus 100 --duration 60s scripts/k6-loadtest.js
k6 run --env SCENARIO=single_ip scripts/k6-loadtest.js
```
