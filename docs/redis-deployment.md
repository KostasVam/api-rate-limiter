# Redis Deployment Guide

## Standalone (Development)

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## Redis Sentinel (High Availability)

Redis Sentinel provides automatic failover. When the master goes down, Sentinel promotes a replica.

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - sentinel1:26379
          - sentinel2:26379
          - sentinel3:26379
      password: your-redis-password  # optional
```

**docker-compose example:**

```yaml
services:
  redis-master:
    image: redis:7-alpine
    command: redis-server --appendonly yes

  redis-replica:
    image: redis:7-alpine
    command: redis-server --replicaof redis-master 6379

  redis-sentinel:
    image: redis:7-alpine
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/redis/sentinel.conf
```

**sentinel.conf:**
```
sentinel monitor mymaster redis-master 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
```

## Redis Cluster (Horizontal Scaling)

Redis Cluster distributes keys across multiple nodes using hash slots (0-16383).

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-node1:6379
          - redis-node2:6379
          - redis-node3:6379
        max-redirects: 3
      password: your-redis-password  # optional
```

### Hash Slot Compatibility

Rate limit keys use Redis hash tags to ensure all keys for the same policy+subject land on the same hash slot:

```
Key format: rl:{policy-id:subject}:window
Hash tag:   {policy-id:subject}
```

**Examples:**
```
rl:{login-per-ip:ip:1.2.3.4}:29034110     → slot = hash({login-per-ip:ip:1.2.3.4})
rl:{login-per-ip:ip:1.2.3.4}:29034111     → same slot (same hash tag)
rl:tb:{payments:user:123}                  → slot = hash({payments:user:123})
```

This is critical for:
- **Sliding Window** — requires 2 keys (current + previous window) in the same Lua script
- **Token Bucket** — single key, but hash tag keeps it consistent

Without hash tags, multi-key Lua scripts would fail with `CROSSSLOT` error in Cluster mode.

### Cluster Sizing

| Metric | Guideline |
|---|---|
| Nodes | Minimum 3 masters + 3 replicas |
| Memory per node | Active keys × ~250 bytes |
| Key distribution | Hash tags ensure per-subject locality |

## Connection Pooling (Lettuce)

Spring Boot's Lettuce client supports connection pooling out of the box:

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2
          max-wait: 2000ms
```

### Recommended settings by scale

| Scale | max-active | max-idle | Notes |
|---|---|---|---|
| Low (< 100 rps) | 8 | 4 | Default is fine |
| Medium (100-1k rps) | 16 | 8 | Increase if seeing pool exhaustion |
| High (> 1k rps) | 32-64 | 16 | Monitor `lettuce.pool.*` metrics |

## Timeout Configuration

```yaml
spring:
  data:
    redis:
      timeout: 2000ms              # command timeout
      connect-timeout: 3000ms      # connection timeout
      lettuce:
        shutdown-timeout: 200ms    # graceful shutdown
```

**Recommendation:** Keep timeouts low (1-3 seconds). The circuit breaker will trip after sustained failures, preventing timeout-induced latency from cascading.

## Monitoring

Key Redis metrics to watch:

| Metric | Command | Alert Threshold |
|---|---|---|
| Memory usage | `INFO memory` | > 80% maxmemory |
| Connected clients | `INFO clients` | > max-active pool |
| Ops/sec | `INFO stats` | Baseline + 50% |
| Key count | `DBSIZE` | Unexpected growth |
| Eviction count | `INFO stats` | > 0 (data loss risk) |

## Eviction Policy

Set Redis `maxmemory-policy` to `volatile-ttl` — this evicts keys with shortest TTL first, which aligns with rate limit window expiration:

```
maxmemory 256mb
maxmemory-policy volatile-ttl
```
