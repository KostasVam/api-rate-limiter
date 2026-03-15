package com.vamva.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.engine.RateLimitEngine;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Servlet filter that intercepts HTTP requests and enforces rate limits.
 *
 * <p>Runs early in the filter chain (before controllers) via {@link OncePerRequestFilter}
 * to prevent double evaluation on forwards/includes.</p>
 *
 * <p>For allowed requests, adds {@code X-RateLimit-*} headers and passes through.
 * For rejected requests, returns HTTP 429 with a JSON error body and
 * {@code Retry-After} header.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitEngine engine;
    private final RateLimitMetrics metrics;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitEngine engine, RateLimitMetrics metrics, ObjectMapper objectMapper) {
        this.engine = engine;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String route = request.getMethod() + " " + request.getRequestURI();

        Optional<RateLimitResult> resultOpt = engine.evaluate(request);

        long durationNanos = System.nanoTime() - startNanos;
        metrics.recordEvaluationTime(durationNanos);

        if (resultOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = resultOpt.get();

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));

        if (result.isAllowed()) {
            metrics.recordRequest(result.getPolicyId(), route, true);
            filterChain.doFilter(request, response);
        } else {
            metrics.recordRequest(result.getPolicyId(), route, false);

            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setHeader("X-RateLimit-Policy", result.getPolicyId());
            response.setContentType("application/json");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "rate_limit_exceeded");
            body.put("message", "Too many requests");
            body.put("limit", result.getLimit());
            body.put("remaining", 0);
            body.put("retry_after_seconds", result.getRetryAfterSeconds());
            body.put("policy", result.getPolicyId());

            try {
                response.getWriter().write(objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                log.error("Failed to serialize rate limit response body", e);
                response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            }
        }
    }
}
