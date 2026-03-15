# ADR-001: Use Redis as Rate Limit Backend

**Status:** Accepted
**Date:** 2026-03-15

## Context

Distributed applications require shared counters to enforce global rate limits across multiple instances. A single application instance cannot maintain accurate request counts when traffic is balanced across a fleet. The backend must support atomic increments, key expiration, and sub-millisecond latency.

## Decision

Use Redis as the primary rate limit counter backend, with Lua scripts for atomic counter operations.

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **In-memory only** | Zero latency, no infrastructure dependency | Cannot enforce limits across instances; counters lost on restart |
| **Database counters (PostgreSQL)** | Durable, already available in most stacks | High latency for per-request operations; row-level locking under contention |
| **Dedicated rate limit service (e.g., Envoy RLS)** | Centralized policy management | Operational complexity; additional network hop; vendor coupling |
| **Distributed cache (Hazelcast/Memcached)** | Shared state without Redis | Hazelcast adds JVM overhead; Memcached lacks atomic increment + TTL in single operation |

## Consequences

### Positive

- Atomic `INCR` + `EXPIRE` via Lua script eliminates race conditions
- Native TTL support automatically cleans up expired windows
- Sub-millisecond latency on local network (p99 typically < 1ms)
- Battle-tested in production rate limiters (Stripe, GitHub, Cloudflare)
- Lettuce client provides non-blocking I/O with connection pooling

### Negative

- Additional infrastructure dependency (Redis must be provisioned and monitored)
- Network partition can cause temporary counter inaccuracy
- Requires fail-open/fail-closed decision when Redis is unavailable (see ADR-004)

### Mitigations

- In-memory backend provided for local development and single-instance deployments
- Configurable fail-open mode prevents Redis outage from blocking all traffic
- Redis Sentinel or Cluster can be used for high availability (not in MVP scope)
