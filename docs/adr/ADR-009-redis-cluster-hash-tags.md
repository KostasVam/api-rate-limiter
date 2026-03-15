# ADR-009: Use Redis Hash Tags for Cluster Compatibility

**Status:** Accepted
**Date:** 2026-03-15

## Context

Redis Cluster distributes keys across 16384 hash slots. Lua scripts that operate on multiple keys require all keys to reside on the same slot, otherwise Redis returns a `CROSSSLOT` error. Our sliding window algorithm uses two keys (current + previous window) in a single Lua script.

## Decision

Use Redis hash tags in all rate limit keys. The hash tag `{policy-id:subject}` ensures all keys for the same policy+subject map to the same hash slot, regardless of window suffix.

### Key Format

```
Before (standalone-only):
  rl:login-per-ip:ip:1.2.3.4:29034110

After (cluster-compatible):
  rl:{login-per-ip:ip:1.2.3.4}:29034110
```

Redis hashes only the content inside `{...}` when computing the slot, so:
- `rl:{login-per-ip:ip:1.2.3.4}:29034110` → slot = `hash(login-per-ip:ip:1.2.3.4)`
- `rl:{login-per-ip:ip:1.2.3.4}:29034111` → same slot

## Consequences

### Positive

- Multi-key Lua scripts work in Redis Cluster without modification
- No `CROSSSLOT` errors
- Transparent — no configuration needed, hash tags are always applied
- Works identically in standalone, Sentinel, and Cluster modes

### Negative

- Keys for the same subject are co-located on one node — cannot distribute a single subject's counters across the cluster
- Hash tag slightly increases key length (~4 bytes for `{` and `}`)
- Hot subjects (e.g., a high-traffic API key) will create hot spots on a single node

### Mitigations

- Hot spot risk is inherent to per-subject rate limiting — the subject itself is the unit of locality
- Redis Cluster can rebalance slots if a node becomes overloaded
- Monitor per-node metrics to detect imbalance
