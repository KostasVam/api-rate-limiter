# API Rate Limiter

Distributed API rate limiter middleware with Redis-backed enforcement, per-route policies, and observability support.

## Overview

A Spring Boot middleware library that limits HTTP request rates per configurable subject (IP, user, API key, tenant). It works across multiple application instances using Redis as a shared counter store, and exposes standard HTTP rate limit headers.

## Tech Stack

| Component       | Choice                           |
|-----------------|----------------------------------|
| Language        | Java 17                          |
| Framework       | Spring Boot 3.4                  |
| Build           | Gradle (Kotlin DSL)              |
| Redis Client    | Lettuce (via spring-data-redis)  |
| Metrics         | Micrometer + Prometheus          |
| Config          | YAML (Spring native)             |
| Validation      | Jakarta Bean Validation          |
| Boilerplate     | Lombok                           |
| Testing         | JUnit 5 + Mockito + Testcontainers |
| Containerization| Docker Compose (Redis)           |

## Features (MVP)

- [x] HTTP middleware (Spring `OncePerRequestFilter`)
- [x] Fixed Window Counter algorithm
- [x] Redis backend (distributed, Lua script for atomicity)
- [x] In-memory backend (local/dev)
- [x] Per-route policy matching (Ant-style path patterns)
- [x] Per-subject rate limiting (IP, user, API key, tenant, route)
- [x] Composite subject scopes
- [x] Standard HTTP rate limit headers
- [x] Configurable fail-open / fail-closed on Redis failure
- [x] Structured logging (SLF4J)
- [x] Prometheus metrics via Micrometer (with route labels)
- [x] YAML-based policy configuration with Bean Validation
- [x] Path normalization (trailing slash stripping)

## Architecture

### Request Flow

```
HTTP Request
     │
     ▼
┌─────────────────────┐
│  RateLimitFilter    │  ← OncePerRequestFilter (middleware entry point)
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PolicyResolver     │  ← Matches request to policies (AntPathMatcher)
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  CompositeKeyBuilder│  ← Builds subject key via SubjectExtractors
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  RateLimitEngine    │  ← Evaluates each matched policy
│                     │
│  ┌───────────────┐  │
│  │ Backend       │  │  ← Redis (prod) or InMemory (dev)
│  │ (INCR+TTL)    │  │
│  └───────────────┘  │
└─────────┬───────────┘
          │
          ▼
    ┌─────────┐
    │ ALLOW?  │
    └────┬────┘
    yes  │  no
     ▼      ▼
  continue  429
```

### Subject Identification

Requests are identified by **subject scope**. Supported scopes:

| Scope     | Example Key                     | Extractor Source                      |
|-----------|---------------------------------|---------------------------------------|
| `ip`      | `ip:203.0.113.10`               | `X-Forwarded-For` / `remoteAddr`      |
| `user`    | `user:123`                      | `UserPrincipal` / `X-User-Id` header  |
| `api_key` | `api_key:client_abc`            | `X-API-Key` header                    |
| `tenant`  | `tenant:acme`                   | `X-Tenant-Id` header                  |
| `route`   | `route:POST:/payments`          | HTTP method + request URI             |

Composite keys are supported: `user:123|route:POST:/api/payments`

### Policy Model

Each policy defines **when** it applies and **what** the limit is:

```yaml
rate-limiter:
  enabled: true
  fail-open: true
  backend: redis              # redis | in-memory

  policies:
    - id: login-per-ip
      enabled: true
      priority: 1
      match:
        paths:
          - /api/auth/login
        methods:
          - POST
      subjects:
        - ip
      limit: 5
      window-seconds: 60

    - id: payments-per-user
      enabled: true
      priority: 2
      match:
        paths:
          - /api/payments/**
        methods:
          - POST
          - PUT
      subjects:
        - user
        - route
      limit: 10
      window-seconds: 60
```

**Evaluation rule:** if ANY policy is exceeded → reject (429). If ALL pass → allow.

When multiple policies fail, the one with the shortest `retry-after` is used for the response.

### Fixed Window Algorithm

Requests are counted in discrete time windows.

```
window_start = floor(current_epoch_seconds / window_seconds)
key = rl:{policy_id}:{subject}:{window_start}
```

**Redis operations (atomic via Lua script):**
1. `INCR key`
2. Set `EXPIRE = window_seconds + 5s buffer` (if new key)
3. Return `{counter, TTL}`

**Example:** policy `payments-per-user`, user `123`, window `60s`
```
Key:   rl:payments-per-user:user:123:28876925
Value: 7  (integer counter)
TTL:   65s
```

### HTTP Behavior

**Allowed request** — normal response with rate limit headers:
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 93
X-RateLimit-Reset: 1742054460
```

**Rejected request:**
```
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1742054460
Retry-After: 42
X-RateLimit-Policy: payments-per-user
Content-Type: application/json

{
  "error": "rate_limit_exceeded",
  "message": "Too many requests",
  "limit": 100,
  "remaining": 0,
  "retry_after_seconds": 42,
  "policy": "payments-per-user"
}
```

### Failure Modes

When Redis is unavailable:

| Mode     | Behavior                              |
|----------|---------------------------------------|
| `open`   | Allow request, log warning, emit metric |
| `closed` | Reject request                        |

Default: `open` (configured via `rate-limiter.fail-open`)

## Observability

### Prometheus Metrics

| Metric                           | Type      | Labels                             |
|----------------------------------|-----------|-------------------------------------|
| `rate_limiter_requests_total`    | Counter   | `policy_id`, `decision`, `route`    |
| `rate_limiter_allowed_total`     | Counter   | `policy_id`, `route`                |
| `rate_limiter_rejected_total`    | Counter   | `policy_id`, `route`                |
| `rate_limiter_errors_total`      | Counter   | —                                   |
| `rate_limiter_eval_duration`     | Timer     | —                                   |

Metrics endpoint: `GET /actuator/prometheus`

### Structured Logs

Each rate limit evaluation logs (SLF4J key-value format):

```
policy_id=payments-per-user subject=user:123 route=POST /api/payments decision=ALLOW remaining=9 retry_after=0
```

## Project Structure

```
api-rate-limiter/
├── src/
│   ├── main/java/com/vamva/ratelimiter/
│   │   ├── RateLimiterApplication.java
│   │   ├── config/
│   │   │   ├── RateLimiterProperties.java
│   │   │   └── RateLimiterAutoConfiguration.java
│   │   ├── model/
│   │   │   ├── Policy.java
│   │   │   ├── MatchCondition.java
│   │   │   └── RateLimitResult.java
│   │   ├── subject/
│   │   │   ├── SubjectExtractor.java
│   │   │   ├── IpExtractor.java
│   │   │   ├── UserExtractor.java
│   │   │   ├── ApiKeyExtractor.java
│   │   │   ├── TenantExtractor.java
│   │   │   ├── RouteExtractor.java
│   │   │   └── CompositeKeyBuilder.java
│   │   ├── policy/
│   │   │   └── PolicyResolver.java
│   │   ├── backend/
│   │   │   ├── RateLimitBackend.java
│   │   │   ├── RedisBackend.java
│   │   │   └── InMemoryBackend.java
│   │   ├── engine/
│   │   │   └── RateLimitEngine.java
│   │   ├── filter/
│   │   │   └── RateLimitFilter.java
│   │   ├── metrics/
│   │   │   └── RateLimitMetrics.java
│   │   └── demo/
│   │       └── DemoController.java
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── scripts/
│   │       └── fixed_window.lua
│   └── test/java/com/vamva/ratelimiter/
│       ├── subject/
│       │   ├── IpExtractorTest.java
│       │   └── CompositeKeyBuilderTest.java
│       ├── policy/
│       │   └── PolicyResolverTest.java
│       ├── backend/
│       │   └── InMemoryBackendTest.java
│       ├── engine/
│       │   └── RateLimitEngineTest.java
│       ├── filter/
│       │   └── RateLimitFilterTest.java
│       └── integration/
│           ├── TestController.java
│           └── RateLimiterIntegrationTest.java
├── docker-compose.yml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Acceptance Criteria

| ID   | Scenario                                                              | Expected                |
|------|-----------------------------------------------------------------------|-------------------------|
| AC1  | Policy 5 req/min/IP — client sends 4 requests                        | All succeed             |
| AC2  | Policy 5 req/min/IP — client sends 6th request                       | HTTP 429                |
| AC3  | User A hits limit — User B sends request                             | User B succeeds         |
| AC4  | Two app instances share Redis — combined count enforced               | Limit shared correctly  |

## Getting Started

### Prerequisites
- Java 17+
- Docker (for Redis and integration tests)

### Run
```bash
# Start Redis
docker compose up -d

# Run the application with demo endpoints
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun

# Test rate limiting (login-per-ip policy: 5 req/min)
curl -X POST http://localhost:8080/api/auth/login

# View metrics
curl http://localhost:8080/actuator/prometheus | grep rate_limiter

# Run tests (Docker required for integration tests)
./gradlew test
```

### Demo Endpoints

Available when running with `demo` profile:

| Method | Path              | Rate Limited By           |
|--------|-------------------|---------------------------|
| POST   | `/api/auth/login` | IP (5 req/min)            |
| POST   | `/api/payments`   | User + Route (10 req/min) |
| GET    | `/api/health`     | Not rate limited          |

## Future Work

- Token Bucket / Sliding Window algorithms
- Admin API for runtime policy management
- Dashboard (Grafana templates)
- Dynamic config reload without restart
- ML-based anomaly detection
- WAF / bot detection integration
- Billing / quota management
