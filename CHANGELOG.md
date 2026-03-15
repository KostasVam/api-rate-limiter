# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-03-15

### Added
- Fixed Window Counter algorithm
- Sliding Window Counter algorithm
- Token Bucket algorithm with configurable burst capacity
- Redis backend with Lua scripts for atomic operations
- In-memory backend for local/dev use
- Resilience4j circuit breaker around Redis calls
- Redis Cluster / Sentinel support via hash tag key design
- HTTP rate limit headers (X-RateLimit-Limit, Remaining, Reset, Retry-After)
- Per-route YAML policy configuration with Ant-style path matching
- Per-policy algorithm selection
- Composite subject scopes (IP, user, API key, tenant, route)
- Pluggable SubjectExtractor interface
- Observe (shadow) mode for safe policy rollout
- Dynamic policy reload via actuator endpoint (GET/POST /actuator/ratelimiter)
- Annotation-based rate limiting with @RateLimit
- Configurable fail-open / fail-closed on backend failure
- Configurable bypass paths for health checks and actuator
- Custom error message and HTTP status code per policy
- Prometheus metrics via Micrometer (low-cardinality labels)
- Structured logging with SLF4J
- Spring Boot Starter packaging (auto-configuration, no component scanning required)
- Spring configuration metadata for IDE autocomplete
- Grafana dashboard template with 8 panels
- GitHub Actions CI pipeline with Redis service container
- k6 load test scripts with 3 scenarios
- Comprehensive test suite: 64 tests (unit, integration, contract, chaos)
- Architecture Decision Records (ADR-001 through ADR-009)
- Security threat model documentation
- Performance characteristics and benchmarks documentation
