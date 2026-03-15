# ADR-004: Default to Fail-Open on Backend Failure

**Status:** Accepted
**Date:** 2026-03-15

## Context

When the Redis backend is unavailable (network partition, crash, timeout), the rate limiter must decide whether to allow or reject incoming requests. This decision has significant availability and security implications.

## Decision

Default to **fail-open**: when Redis is unreachable, allow requests through and log a warning with a metric increment.

## Alternatives Considered

| Mode | Behavior | Risk |
|---|---|---|
| **Fail-open** | Allow all requests when backend is down | Temporary loss of rate limiting; potential abuse during outage |
| **Fail-closed** | Reject all requests when backend is down | Complete service outage tied to Redis availability |
| **Cached counters** | Fall back to in-memory counters during outage | Complex state management; counters diverge across instances |

## Consequences

### Positive

- Application availability is not coupled to Redis availability
- A Redis outage does not cascade into a full service outage
- Aligns with industry practice (Stripe, GitHub, Cloudflare all default to fail-open)
- Backend errors are observable via `rate_limiter_errors_total` metric and structured logs

### Negative

- During a Redis outage, rate limits are not enforced — abusive clients could exploit the window
- Silent degradation may go unnoticed without proper alerting on the error metric

### Operational Guidance

- **Monitor** `rate_limiter_errors_total` and alert on sustained non-zero values
- **Fail-closed** mode is available via `rate-limiter.fail-open: false` for use cases where rate limiting is a security requirement (e.g., authentication endpoints)
- Consider a hybrid approach in future: fail-open for general API endpoints, fail-closed for security-sensitive routes
