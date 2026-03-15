# ADR-003: Implement as Servlet Filter Middleware

**Status:** Accepted
**Date:** 2026-03-15

## Context

The rate limiter must intercept HTTP requests before they reach application logic. The integration point determines how tightly coupled the limiter is to the application framework and how much control it has over the request/response lifecycle.

## Decision

Implement the rate limiter as a Spring `OncePerRequestFilter` that runs early in the filter chain, before controller dispatch.

## Alternatives Considered

| Approach | Pros | Cons |
|---|---|---|
| **Servlet Filter** | Framework-level, runs before controllers, access to full request/response | Tied to servlet API |
| **Spring Interceptor (HandlerInterceptor)** | Access to handler metadata (annotations) | Runs after filter chain; cannot prevent request from reaching DispatcherServlet |
| **AOP / Annotation-based (@RateLimit)** | Fine-grained per-method control | Requires code changes in every controller; cannot protect unannotated endpoints |
| **Reverse proxy (NGINX/Envoy)** | Zero application code | Separate infrastructure; limited policy flexibility; harder to tie to application-level subjects (user ID, tenant) |
| **Spring Cloud Gateway filter** | Reactive, high performance | Only works with gateway topology; not embeddable in arbitrary Spring Boot apps |

## Consequences

### Positive

- Intercepts requests before any controller logic executes — rejected requests consume minimal server resources
- `OncePerRequestFilter` prevents double evaluation on servlet forwards/includes
- Filter ordering via `@Order` ensures rate limiting runs before authentication or other filters if needed
- Full access to `HttpServletRequest` for subject extraction (headers, remote addr, principal)
- Response can be fully controlled (status code, headers, JSON body) without involving Spring MVC

### Negative

- Coupled to the Servlet API (Jakarta EE) — does not work with reactive (WebFlux) stacks
- No access to Spring MVC handler metadata (e.g., `@RequestMapping` annotations) at filter time — policy matching uses URL patterns instead of handler method names

### Design Details

- Filter is registered with `@Order(Ordered.HIGHEST_PRECEDENCE + 10)` — high priority but leaves room for CORS or security filters that must run first
- Rate limit headers (`X-RateLimit-*`) are set on both allowed and rejected responses for client visibility
- Rejected responses short-circuit the filter chain — `filterChain.doFilter()` is not called
