# ADR-002: Use Fixed Window Counter Algorithm

**Status:** Accepted
**Date:** 2026-03-15

## Context

Rate limiting algorithms trade off between accuracy, implementation complexity, and resource consumption. The MVP needs an algorithm that is simple to implement correctly, easy to reason about, and efficient in Redis.

## Decision

Use the Fixed Window Counter algorithm for the MVP. The window start is computed as `floor(epoch_seconds / window_size)`, and each window gets its own Redis key with a TTL slightly longer than the window duration.

## Alternatives Considered

| Algorithm | Accuracy | Complexity | Redis Cost |
|---|---|---|---|
| **Fixed Window** | Good (boundary spike possible) | Low | 1 key per window per subject |
| **Sliding Window Log** | Exact | High | 1 sorted set per subject, O(n) operations |
| **Sliding Window Counter** | Very good (weighted average) | Medium | 2 keys per subject (current + previous window) |
| **Token Bucket** | Very good (burst-friendly) | Medium | 1 key + last refill timestamp |
| **Leaky Bucket** | Smooth output rate | Medium | Queue-based, harder to distribute |

## Consequences

### Positive

- Single `INCR` + conditional `EXPIRE` — one Redis round trip, O(1)
- Deterministic: same request at same time always produces same decision
- Easy to debug: key name encodes policy, subject, and window
- Natural TTL cleanup — no background garbage collection needed

### Negative

- **Boundary spike problem:** a burst at the end of window N and start of window N+1 can allow up to 2x the limit in a short period
- Not suitable for use cases requiring smooth rate enforcement

### Boundary Spike Example

```
Window 1 (00:00-01:00): limit=100
  - 0 requests at 00:00-00:59
  - 100 requests at 00:59     ← allowed
Window 2 (01:00-02:00): limit=100
  - 100 requests at 01:00     ← allowed
  = 200 requests in 1 second  ← exceeds intended rate
```

### Future Migration Path

Token Bucket or Sliding Window Counter can be added as alternative algorithms behind the `RateLimitBackend` interface without changing the filter, engine, or policy model. The `algorithm` field is reserved in the policy schema for this purpose.
