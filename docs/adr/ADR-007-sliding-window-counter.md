# ADR-007: Add Sliding Window Counter Algorithm

**Status:** Accepted
**Date:** 2026-03-15

## Context

The Fixed Window Counter algorithm (ADR-002) is simple and efficient but has a known boundary spike problem: a burst at the end of window N and start of window N+1 can allow up to 2x the intended rate. For APIs where smooth rate enforcement matters (e.g., payment endpoints, external API quotas), a more accurate algorithm is needed.

## Decision

Add Sliding Window Counter as a second algorithm option, selectable per policy via the `algorithm` field.

### How It Works

Instead of discrete windows, the sliding window counter computes a **weighted average** of the current and previous window counters:

```
weighted_count = current_count + (previous_count * overlap_weight)
```

Where `overlap_weight` represents how far into the current window we are:

```
overlap_weight = 1.0 - (elapsed_seconds_in_window / window_seconds)
```

**Example:** limit=100 req/min, 40 seconds into the current window:

```
previous window count: 80
current window count:  30
overlap_weight = 1.0 - (40/60) = 0.333

weighted = 30 + (80 * 0.333) = 56.67 → 57 requests
remaining = 100 - 57 = 43
```

### Redis Implementation

A Lua script atomically:
1. `INCR` the current window key
2. `GET` the previous window key
3. Return `{current, previous, ttl, weight}` for the caller to compute the weighted count

This requires **2 keys** per subject per policy (current + previous), versus 1 for fixed window.

## Alternatives Considered

| Algorithm | Accuracy | Redis Keys | Redis Ops | Complexity |
|---|---|---|---|---|
| Fixed Window (existing) | Good | 1 | 1 INCR | Low |
| **Sliding Window Counter (chosen)** | Very good | 2 | 1 INCR + 1 GET | Low-Medium |
| Sliding Window Log | Exact | 1 sorted set | ZADD + ZRANGEBYSCORE + ZCARD | High |
| Token Bucket | Good (burst-friendly) | 1 | GET + SET | Medium |

Sliding Window Counter was chosen because it provides significantly better accuracy than fixed window with minimal additional cost (one extra GET per request).

## Consequences

### Positive

- Eliminates boundary spike problem: a burst across window boundaries is correctly counted
- Minimal cost increase: one additional `GET` for the previous window key (which is already in Redis memory)
- Per-policy configuration: can mix fixed window and sliding window in the same deployment
- Previous window key is naturally cleaned up by TTL

### Negative

- Slightly higher Redis memory: 2 active keys per subject instead of 1 (negligible in practice)
- Weighted count uses `ceil()` rounding, which may occasionally be off by 1 request
- More complex to debug: effective count depends on timing within the window

### Configuration

```yaml
policies:
  - id: payments-per-user
    algorithm: sliding_window    # or: fixed_window (default)
    limit: 10
    window-seconds: 60
    subjects: [user]
```
