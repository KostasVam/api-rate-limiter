package com.vamva.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus metrics instrumentation for the rate limiter.
 *
 * <p>Provides counters for request decisions (allowed/rejected), backend errors,
 * and a timer for evaluation duration. Meter instances are cached to avoid
 * registry lookups on every request.</p>
 *
 * <p>Exposed metrics:</p>
 * <ul>
 *   <li>{@code rate_limiter_requests_total} — total evaluated requests (policy_id, decision, route)</li>
 *   <li>{@code rate_limiter_allowed_total} — allowed requests (policy_id, route)</li>
 *   <li>{@code rate_limiter_rejected_total} — rejected requests (policy_id, route)</li>
 *   <li>{@code rate_limiter_errors_total} — backend failures</li>
 *   <li>{@code rate_limiter_eval_duration} — evaluation time histogram</li>
 * </ul>
 */
@Component
public class RateLimitMetrics {

    private final MeterRegistry registry;
    private final Timer evalTimer;
    private final Counter errorCounter;
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.evalTimer = Timer.builder("rate_limiter_eval_duration")
                .description("Time spent evaluating rate limit policies")
                .register(registry);
        this.errorCounter = Counter.builder("rate_limiter_errors_total")
                .description("Total backend errors")
                .register(registry);
    }

    /**
     * Records a rate limit decision for the given policy and route.
     *
     * @param policyId the evaluated policy
     * @param route    the request route (e.g., "POST /api/payments")
     * @param allowed  whether the request was allowed
     */
    public void recordRequest(String policyId, String route, boolean allowed) {
        String decision = allowed ? "allowed" : "rejected";

        getOrCreateCounter("rate_limiter_requests_total", policyId, route, decision).increment();

        if (allowed) {
            getOrCreateCounter("rate_limiter_allowed_total", policyId, route, null).increment();
        } else {
            getOrCreateCounter("rate_limiter_rejected_total", policyId, route, null).increment();
        }
    }

    /**
     * Records the time taken to evaluate rate limit policies.
     *
     * @param nanos duration in nanoseconds
     */
    public void recordEvaluationTime(long nanos) {
        evalTimer.record(Duration.ofNanos(nanos));
    }

    /** Increments the backend error counter. */
    public void recordBackendError() {
        errorCounter.increment();
    }

    private Counter getOrCreateCounter(String name, String policyId, String route, String decision) {
        String cacheKey = name + ":" + policyId + ":" + route + ":" + decision;
        return counterCache.computeIfAbsent(cacheKey, k -> {
            Counter.Builder builder = Counter.builder(name)
                    .tag("policy_id", policyId)
                    .tag("route", route);
            if (decision != null) {
                builder.tag("decision", decision);
            }
            return builder.register(registry);
        });
    }
}
