# ADR-008: Add Token Bucket Algorithm

**Status:** Accepted
**Date:** 2026-03-15

## Context

Fixed window and sliding window algorithms enforce a strict request count per time window. Some use cases require allowing short bursts (e.g., a client sending 5 requests instantly) while enforcing a long-term average rate. Neither window-based algorithm handles this well — they reject immediately once the count exceeds the limit, regardless of burst patterns.

## Decision

Add Token Bucket as a third algorithm option, selectable per policy.

### How It Works

Each subject has a virtual bucket of tokens:
- Bucket starts at full capacity
- Tokens refill at a steady rate: `refill_rate = limit / window_seconds`
- Each request consumes 1 token
- If the bucket is empty, the request is rejected
- Bucket capacity can exceed the refill rate to allow bursts

### Configuration

```yaml
- id: api-per-key
  algorithm: token_bucket
  limit: 100              # 100 tokens per 60 seconds (refill rate)
  window-seconds: 60
  burst-capacity: 20      # bucket can hold up to 20 tokens (optional, defaults to limit)
```

**Refill rate** = `limit / window_seconds` = 1.67 tokens/second
**Burst** = up to 20 instant requests if bucket is full

### Redis Implementation

A Lua script atomically:
1. Reads current tokens and last refill timestamp from a Redis hash
2. Computes refilled tokens based on elapsed time
3. Attempts to consume one token
4. Returns `{allowed, remaining, retry_after}`

Redis key uses a hash (`HMSET`) with fields `tokens` and `last_refill` instead of a simple counter.

## Alternatives Considered

The three algorithms now cover the main rate limiting strategies:

| Algorithm | Best For | Burst Handling |
|---|---|---|
| Fixed Window | Simple limits, low overhead | No burst control |
| Sliding Window | Smooth enforcement, no boundary spikes | No burst control |
| **Token Bucket** | APIs with bursty traffic patterns | Allows controlled bursts |

## Consequences

### Positive

- Allows legitimate burst patterns (e.g., page load triggering multiple API calls)
- Separate `burstCapacity` from `limit` gives fine-grained control
- Long-term average rate is still enforced
- Single Redis hash per subject (no window-based key proliferation)

### Negative

- More complex Redis storage (hash vs integer counter)
- Token refill depends on wall clock — clock skew between app instances can cause slight inconsistencies
- Slightly less intuitive than "N requests per minute" for operators to reason about
