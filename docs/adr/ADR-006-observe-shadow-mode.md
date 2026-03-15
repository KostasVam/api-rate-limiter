# ADR-006: Observe (Shadow) Mode for Safe Policy Rollout

**Status:** Accepted
**Date:** 2026-03-15

## Context

Deploying new rate limiting policies in production carries risk. An overly aggressive policy can block legitimate traffic. An overly permissive one provides no protection. There is no safe way to validate a policy's impact without observing real traffic.

## Decision

Add an `observe` mode to policies. In observe mode, the full evaluation pipeline runs — counters are incremented, metrics and logs are recorded — but requests are never rejected. This allows operators to:

1. Deploy a new policy in `observe` mode
2. Monitor `rate_limiter_observed_would_reject_total` to see how many requests *would have been* rejected
3. Tune limits based on real traffic data
4. Switch to `enforce` mode with confidence

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **Dry-run flag on the limiter** | Simple, global toggle | Cannot observe individual policies while enforcing others |
| **Log-only backend** | No counter side effects | Cannot measure real counter behavior (race conditions, window boundaries) |
| **Separate shadow deployment** | Full isolation | Operational complexity, doesn't see real traffic patterns |
| **Per-policy mode (chosen)** | Granular control, real counters, production traffic | Slightly more complex policy model |

## Consequences

### Positive

- Zero-risk policy rollout: observe first, enforce after validation
- Real counter behavior: observe mode increments the same counters as enforce mode, so window boundaries and concurrency behave identically
- Per-policy granularity: can observe one policy while enforcing all others
- Dedicated metric (`rate_limiter_observed_would_reject_total`) makes dashboarding straightforward
- Structured logs include `mode=observe` and `decision=OBSERVE_WOULD_REJECT` for filtering

### Negative

- Observe-mode policies still consume Redis keys and memory (counters are real)
- Operators must remember to switch from `observe` to `enforce` — there is no automatic promotion

### Configuration

```yaml
rate-limiter:
  policies:
    - id: new-strict-policy
      mode: observe          # observe | enforce (default)
      match:
        paths: [/api/**]
        methods: [POST]
      subjects: [ip]
      limit: 3
      window-seconds: 60
```

### Metrics

| Metric | Description |
|---|---|
| `rate_limiter_observed_would_reject_total` | Requests that would have been rejected if the policy were in enforce mode |
