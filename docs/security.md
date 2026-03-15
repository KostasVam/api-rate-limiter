# Security Considerations & Threat Model

## Overview

A rate limiter is a security-sensitive component. If bypassed or misconfigured, it fails to protect downstream services. This document outlines known threats and their mitigations.

## Threat Model

### 1. IP Spoofing via X-Forwarded-For

**Threat:** Attacker crafts `X-Forwarded-For` header to impersonate a different IP, evading per-IP rate limits.

**Impact:** Rate limit bypass for IP-scoped policies.

**Mitigations:**
- Configure trusted proxy IPs at the load balancer level — only accept `X-Forwarded-For` from known proxies
- The `IpExtractor` uses the **leftmost** entry (original client IP), not the rightmost
- Fall back to `remoteAddr` when header is absent or malformed
- In zero-trust environments, consider using `remoteAddr` exclusively

**Residual risk:** Medium — depends on deployment topology.

### 2. Key Explosion Attack

**Threat:** Attacker generates requests with high-cardinality subjects (e.g., random `X-API-Key` values) to exhaust Redis memory with unique rate limit keys.

**Impact:** Redis memory exhaustion → OOM → backend failure → fail-open allows all traffic.

**Example attack pattern:**
```
X-API-Key: random_key_1  →  rl:policy:api_key:random_key_1:28876925
X-API-Key: random_key_2  →  rl:policy:api_key:random_key_2:28876925
X-API-Key: random_key_3  →  rl:policy:api_key:random_key_3:28876925
... millions of unique keys
```

**Mitigations:**
- TTL on all Redis keys ensures automatic cleanup (window + 5s buffer)
- Validate API keys upstream before rate limiting (authentication middleware should run first)
- Monitor Redis memory usage and key count (`INFO memory`, `DBSIZE`)
- Set Redis `maxmemory` with `volatile-ttl` eviction policy
- Consider adding a global per-IP rate limit as a first line of defense (limits key creation rate)

**Residual risk:** Medium — requires upstream authentication for full mitigation.

### 3. Header Manipulation

**Threat:** Attacker modifies rate limit subject headers (`X-User-Id`, `X-API-Key`, `X-Tenant-Id`) to assume a different identity.

**Impact:** Rate limit evasion or attribution to wrong subject.

**Mitigations:**
- Subject headers must be set by trusted upstream services (API gateway, auth middleware), not by clients directly
- `UserExtractor` prefers `UserPrincipal` (set by authentication framework) over the `X-User-Id` header
- In production, strip client-provided `X-User-Id` and `X-Tenant-Id` headers at the edge proxy

**Residual risk:** Low — if upstream authentication is properly configured.

### 4. Redis Abuse / Data Exfiltration

**Threat:** If Redis is accessible from untrusted networks, an attacker could read rate limit keys (leaking user IDs, IPs) or flush counters.

**Impact:** Privacy leak, rate limit bypass.

**Mitigations:**
- Redis should not be exposed to the internet — bind to private network only
- Enable Redis AUTH (password or ACL)
- Use TLS for Redis connections in production
- Rate limit keys contain subject values (IPs, user IDs) — consider hashing subjects in high-security environments

**Residual risk:** Low — standard Redis hardening practices apply.

### 5. Time-of-Check to Time-of-Use (TOCTOU)

**Threat:** Between checking the counter and processing the request, additional requests may arrive, causing slight over-admission.

**Impact:** Marginal limit overshoot (1-2 extra requests under extreme concurrency).

**Mitigations:**
- Lua script ensures atomic increment + check in a single Redis operation
- The counter is incremented **before** checking the limit, not after — this means the decision reflects the post-increment count
- Acceptable trade-off: 1-2 extra requests is negligible at the scale where this matters

**Residual risk:** Negligible.

### 6. Denial of Service via Rate Limiter Latency

**Threat:** If Redis is slow (network issues, overloaded), every request incurs high latency waiting for the rate limit check.

**Impact:** Rate limiter becomes the bottleneck, degrading overall application performance.

**Mitigations:**
- Lettuce client supports connection timeouts (configurable via Spring properties)
- Fail-open mode ensures requests proceed even if Redis is slow/down
- `rate_limiter_eval_duration` metric exposes evaluation latency for alerting
- Consider adding a circuit breaker around Redis calls in future iterations

**Residual risk:** Low — fail-open mode prevents cascading failure.

## Security Checklist

| Item | Status |
|---|---|
| Subject headers validated by upstream auth | Deployment responsibility |
| Redis not exposed to public network | Deployment responsibility |
| Redis AUTH enabled | Deployment responsibility |
| TTL set on all rate limit keys | Implemented (window + 5s) |
| Fail-open mode with error metrics | Implemented |
| Atomic counter operations (Lua script) | Implemented |
| IP extraction from trusted source | Implemented (configurable) |
| Rate limit bypass monitoring | Implemented (Prometheus metrics) |
