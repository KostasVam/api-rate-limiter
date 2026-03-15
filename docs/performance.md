# Performance Characteristics

## Measured Latency

Each rate-limited request adds one synchronous Redis round trip to the request path. All numbers below are from JMH microbenchmarks and k6 HTTP load tests.

### Per-Request Overhead (Measured)

| Operation | Measured Latency | Source |
|---|---|---|
| Policy resolution (5 policies) | ~1.6 μs | JMH |
| Policy resolution (100 policies) | ~26 μs | JMH |
| Subject extraction (single) | ~76 ns | JMH |
| Subject extraction (triple) | ~250 ns | JMH |
| Algorithm computation | < 1 μs | JMH |
| Redis Lua script execution | 1-3 ms | k6 (local Docker) |
| **Total filter overhead (p95)** | **~3 ms** | k6 |

### HTTP Latency (Measured with k6)

| Percentile | Measured (local Redis) |
|---|---|
| p50 | 1.0 - 1.4 ms |
| p95 | 2.3 - 3.2 ms |
| p99 | 3.5 - 4.1 ms |

Measured via `rate_limiter_eval_duration` histogram.

## Throughput

### Bottleneck Analysis

The primary bottleneck is Redis round-trip time, not CPU. In-process algorithm throughput ranges from 12-26 million ops/sec (JMH).

| Component | Measured Throughput | Bottleneck? |
|---|---|---|
| Fixed Window algorithm | 12.5M ops/sec | No |
| Sliding Window algorithm | 25.9M ops/sec | No |
| Token Bucket algorithm | 25.2M ops/sec | No |
| Policy resolution (20 policies) | 167K ops/sec | No |
| Redis (single instance) | ~100K ops/sec | **Yes** |

## Memory Model

### Redis Memory

Active keys at any point in time:

```
active_keys ≈ active_subjects × matching_policies × 1 (current window only)
```

Each key consumes approximately:
- Key: ~80-150 bytes
- Value: 8 bytes (integer counter) or ~50 bytes (token bucket hash)
- Redis overhead: ~80 bytes per key

**Example:** 10,000 active users × 2 policies = 20,000 keys × ~250 bytes ≈ **5 MB**

Keys are automatically evicted by TTL (window_seconds + 5s buffer).

### JVM Memory

- Policy list: negligible (loaded once at startup)
- Metrics cache: O(policies) `Counter` instances (low-cardinality labels)
- In-memory backend: O(active_keys) entries with scheduled cleanup

## Load Testing

### Tools

- **JMH** — microbenchmarks for algorithm, resolution, key building throughput
- **k6** — HTTP load tests with rate limiter overhead measurement

### Running Benchmarks

```bash
# JMH microbenchmarks (all algorithms, policy resolution, key building)
./gradlew jmh

# k6 HTTP load tests
docker compose up -d
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun
k6 run --vus 100 --duration 60s scripts/k6-loadtest.js

# Specific k6 scenario
k6 run --env SCENARIO=single_ip scripts/k6-loadtest.js
```

### Key Takeaways

1. **Algorithm computation is negligible** — millions of ops/sec in-process
2. **Redis round-trip dominates** — ~1-3ms per request (local network)
3. **Total overhead is ~3ms (p95)** — acceptable for most APIs
4. **Passthrough is free** — excluded paths bypass the engine entirely
5. **Scales with policy count** — linear but fast (26μs at 100 policies)

See [benchmarks.md](benchmarks.md) for detailed results and methodology.
