# ADR-005: Composite Subject Keys for Rate Limit Scoping

**Status:** Accepted
**Date:** 2026-03-15

## Context

Rate limits must be scoped to different dimensions depending on the use case. A login endpoint should be limited per IP, while a payment endpoint should be limited per user per route. The key generation strategy must be flexible enough to support these combinations without code changes.

## Decision

Support composite subject keys built from ordered lists of extractors, joined with `|` as delimiter. The subject list is defined per policy in YAML configuration.

Example: `subjects: [user, route]` produces key `user:123|route:POST:/api/payments`

## Alternatives Considered

| Approach | Pros | Cons |
|---|---|---|
| **Single scope per policy** | Simple | Cannot express "per user per route" without multiple policies |
| **Composite key (chosen)** | Flexible, single policy covers complex scoping | Slightly more complex key builder; higher Redis key cardinality |
| **Hierarchical scoping** | Natural parent-child relationships | Complex to implement; unclear semantics for cross-cutting scopes |

## Consequences

### Positive

- A single policy can express multi-dimensional rate limits (e.g., per tenant per route)
- Extractors are pluggable — new subject types can be added by implementing `SubjectExtractor`
- Key format is human-readable for debugging: `rl:policy:user:123|route:POST:/api/payments:28876925`

### Negative

- Composite keys increase Redis key cardinality (subjects × routes × windows)
- If any extractor in the composite fails to extract a value, the entire policy is skipped for that request
- `|` delimiter must not appear in subject values (unlikely but not validated)

### Key Format

```
rl:{policy_id}:{scope1}:{value1}|{scope2}:{value2}:{window_start}
```

### Cardinality Considerations

| Scope Combination | Example Cardinality |
|---|---|
| `ip` only | ~unique IPs per window |
| `user` only | ~active users per window |
| `user, route` | ~active users × distinct routes |
| `tenant, route` | ~tenants × routes (usually small) |

Monitor Redis memory usage and key count in production. Consider adding a cardinality warning metric in future iterations.
