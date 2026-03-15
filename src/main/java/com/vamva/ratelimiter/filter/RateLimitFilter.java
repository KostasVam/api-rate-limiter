package com.vamva.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.engine.RateLimitEngine;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.vamva.ratelimiter.policy.RouteNormalizer;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
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
public class RateLimitFilter extends OncePerRequestFilter {

    /** Request attribute key where the rate limit result is stored for error handlers. */
    public static final String RATE_LIMIT_RESULT_ATTRIBUTE = "rateLimitResult";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimitEngine engine;
    private final RateLimitMetrics metrics;
    private final ObjectMapper objectMapper;
    private final List<String> excludePaths;

    public RateLimitFilter(RateLimitEngine engine, RateLimitMetrics metrics,
                           ObjectMapper objectMapper, RateLimiterProperties properties) {
        this.engine = engine;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.excludePaths = properties.getExcludePaths();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isExcluded(RouteNormalizer.normalizePath(request.getRequestURI()))) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNanos = System.nanoTime();
        String route = RouteNormalizer.logRoute(request);

        Optional<RateLimitResult> resultOpt = engine.evaluate(request);

        long durationNanos = System.nanoTime() - startNanos;
        metrics.recordEvaluationTime(durationNanos);

        if (resultOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = resultOpt.get();

        // Store result as request attribute for downstream error handlers
        request.setAttribute(RATE_LIMIT_RESULT_ATTRIBUTE, result);

        setRateLimitHeaders(response, result);

        if (result.isAllowed()) {
            metrics.recordRequest(result.getPolicyId(), true);
            filterChain.doFilter(request, response);
        } else {
            metrics.recordRequest(result.getPolicyId(), false);

            response.setStatus(result.getErrorStatusCode());
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setHeader("X-RateLimit-Policy", result.getPolicyId());
            response.setContentType("application/json");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "rate_limit_exceeded");
            body.put("message", result.getErrorMessage());
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

    private void setRateLimitHeaders(HttpServletResponse response, RateLimitResult result) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));
    }

    private boolean isExcluded(String path) {
        return excludePaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
