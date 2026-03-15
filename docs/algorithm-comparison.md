# Algorithm Comparison

This library provides three rate limiting algorithms, selectable per policy. This document explains when to use each, their trade-offs, and how they behave at boundary conditions.

## Quick Reference

| | Fixed Window | Sliding Window Counter | Token Bucket |
|---|---|---|---|
| **Best for** | Simple limits, high throughput | Smooth enforcement | Bursty traffic patterns |
| **Accuracy** | Good | Very good | Good (burst-aware) |
| **Boundary spikes** | Up to 2x limit possible | Eliminated | N/A (no windows) |
| **Redis keys per subject** | 1 | 2 (current + previous) | 1 (hash) |
| **Redis ops per request** | 1 INCR | 1 INCR + 1 GET | 1 HMGET + 1 HMSET |
| **Burst control** | No | No | Yes (configurable) |
| **Complexity** | Low | Low-Medium | Medium |
| **Config** | `algorithm: fixed_window` | `algorithm: sliding_window` | `algorithm: token_bucket` |

## Fixed Window Counter

### How It Works

Divides time into discrete windows. All requests in the same window share a counter.

```
Window 1 [00:00 - 01:00]     Window 2 [01:00 - 02:00]
  counter: 0→1→2→...→N         counter: 0→1→2→...
```

### Implementation

```
window_start = floor(epoch_seconds / window_seconds)
key = rl:{policy}:{subject}:{window_start}
```

Single Lua script: `INCR key` + conditional `EXPIRE`.

### Boundary Spike Problem

```
Window 1:                          Window 2:
|                    ████|████                    |
                     ↑ 100 requests at 00:59
                          ↑ 100 requests at 01:00
= 200 requests in 1 second (limit was 100/min)
```

### When to Use

- High-throughput APIs where simplicity matters most
- Internal services where boundary precision is not critical
- When Redis ops per request must be minimized (1 op)
- Default choice when unsure

### Configuration

```yaml
algorithm: fixed_window
limit: 100
window-seconds: 60
```

## Sliding Window Counter

### How It Works

Computes a weighted average of the current and previous window counters, eliminating boundary spikes.

```
Previous window          Current window
count: 80                count: 30
                    ├────────────┤
                    now (40s into window)

overlap_weight = 1.0 - (40/60) = 0.333
weighted_count = 30 + (80 × 0.333) = 56.67 → 57
```

### Implementation

Lua script: `INCR current_key` + `GET previous_key`. The engine computes the weighted count.

### Why It Eliminates Boundary Spikes

The previous window's contribution gradually fades as time progresses through the current window. A burst at the boundary is counted against both the old and new window proportionally.

```
At window start:  weight=1.0 → previous fully counts
At window middle: weight=0.5 → previous half counts
At window end:    weight=0.0 → previous ignored
```

### Trade-offs

- +1 Redis GET per request (reads previous window counter)
- Slightly higher memory (2 active keys per subject instead of 1)
- `ceil()` rounding may be off by 1 request under edge conditions
- More complex to debug (effective count depends on timing)

### When to Use

- Public APIs where rate limit fairness matters
- Financial/payment endpoints where boundary abuse is a concern
- When you want better accuracy than fixed window without the complexity of token bucket
- When burst control is not needed

### Configuration

```yaml
algorithm: sliding_window
limit: 100
window-seconds: 60
```

## Token Bucket

### How It Works

Each subject has a virtual bucket of tokens. Tokens refill at a steady rate. Each request consumes one token. If the bucket is empty, the request is rejected.

```
Bucket capacity: 20 tokens
Refill rate: 1.67 tokens/sec (100/60)

Time 0:    [████████████████████] 20 tokens (full)
Burst:     [                    ] 0 tokens (20 requests instantly)
+6 sec:    [██████████          ] 10 tokens (refilled 1.67/s × 6s)
+12 sec:   [████████████████████] 20 tokens (full again)
```

### Implementation

Redis hash with `tokens` and `last_refill` fields. Lua script atomically refills and consumes.

```
HMGET bucket_key tokens last_refill
→ compute refill based on elapsed time
→ consume 1 token (or reject)
HMSET bucket_key tokens new_value last_refill now
```

### Burst Capacity vs Refill Rate

Two distinct parameters:

| Parameter | Meaning | Config |
|---|---|---|
| `limit` | Tokens added per `window-seconds` (long-term rate) | `limit: 100, window-seconds: 60` |
| `burst-capacity` | Maximum tokens in bucket (instant burst) | `burst-capacity: 20` |

**Example:** limit=100/min, burst-capacity=20
- Long-term: 100 requests per minute (1.67/sec)
- Short-term: up to 20 requests instantly
- After burst: 1.67 req/sec until bucket refills

If `burst-capacity` is not set, it defaults to `limit`.

### When to Use

- APIs with naturally bursty traffic (page loads, batch operations)
- Mobile clients that send multiple requests on app open
- When you want to allow short bursts while enforcing average rate
- When you need `Retry-After` to be precise (based on refill rate)

### Configuration

```yaml
algorithm: token_bucket
limit: 100
window-seconds: 60
burst-capacity: 20     # optional, defaults to limit
```

## Decision Matrix

| Scenario | Recommended Algorithm |
|---|---|
| Internal microservice-to-microservice | Fixed Window |
| Public REST API | Sliding Window |
| API with webhook/batch patterns | Token Bucket |
| Login/auth brute-force protection | Fixed Window or Sliding Window |
| Payment/financial endpoints | Sliding Window |
| File upload endpoints | Token Bucket |
| High-throughput, low-latency critical | Fixed Window |
| "I'm not sure" | Fixed Window (simplest, good enough for most) |

## Performance Comparison

Measured with in-memory backend (isolating algorithm cost from Redis latency):

| Algorithm | Ops/μs (same key) | Redis Keys | Redis Ops |
|---|---|---|---|
| Fixed Window | Highest | 1 | 1 |
| Sliding Window | ~90% of Fixed Window | 2 | 2 |
| Token Bucket | ~80% of Fixed Window | 1 (hash) | 1 (HMGET+HMSET) |

*Token bucket is slightly slower due to floating-point refill calculations and per-bucket synchronization in the in-memory backend. With Redis, the difference is negligible (dominated by network latency).*

## Mixing Algorithms

Different policies can use different algorithms in the same deployment:

```yaml
policies:
  # Simple per-IP protection
  - id: global-per-ip
    algorithm: fixed_window
    limit: 1000
    window-seconds: 60
    subjects: [ip]

  # Precise per-user enforcement
  - id: api-per-user
    algorithm: sliding_window
    limit: 100
    window-seconds: 60
    subjects: [user]

  # Burst-friendly upload limit
  - id: upload-per-user
    algorithm: token_bucket
    limit: 10
    window-seconds: 60
    burst-capacity: 5
    subjects: [user]
```

All policies are evaluated independently. If any policy rejects, the request is rejected.
