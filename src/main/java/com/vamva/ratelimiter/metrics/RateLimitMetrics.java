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
 * <p>Uses low-cardinality labels only ({@code policy_id}, {@code decision}) to prevent
 * metric explosion from dynamic URI segments. The {@code policy_id} implicitly encodes
 * the route pattern it covers.</p>
 *
 * <p>Exposed metrics:</p>
 * <ul>
 *   <li>{@code rate_limiter_requests_total} — total evaluated requests (policy_id, decision)</li>
 *   <li>{@code rate_limiter_allowed_total} — allowed requests (policy_id)</li>
 *   <li>{@code rate_limiter_rejected_total} — rejected requests (policy_id)</li>
 *   <li>{@code rate_limiter_observed_would_reject_total} — shadow mode rejections (policy_id)</li>
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
     * Records a rate limit decision for the given policy.
     *
     * @param policyId the evaluated policy (low-cardinality, safe for metric labels)
     * @param allowed  whether the request was allowed
     */
    public void recordRequest(String policyId, boolean allowed) {
        String decision = allowed ? "allowed" : "rejected";

        getOrCreateCounter("rate_limiter_requests_total", policyId, decision).increment();

        if (allowed) {
            getOrCreateCounter("rate_limiter_allowed_total", policyId, null).increment();
        } else {
            getOrCreateCounter("rate_limiter_rejected_total", policyId, null).increment();
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

    /**
     * Records a shadow mode rejection — the policy would have rejected, but didn't.
     *
     * @param policyId the observed policy
     */
    public void recordObservedRejection(String policyId) {
        getOrCreateCounter("rate_limiter_observed_would_reject_total", policyId, null).increment();
    }

    /** Increments the backend error counter. */
    public void recordBackendError() {
        errorCounter.increment();
    }

    private Counter getOrCreateCounter(String name, String policyId, String decision) {
        String cacheKey = name + ":" + policyId + ":" + decision;
        return counterCache.computeIfAbsent(cacheKey, k -> {
            Counter.Builder builder = Counter.builder(name)
                    .tag("policy_id", policyId);
            if (decision != null) {
                builder.tag("decision", decision);
            }
            return builder.register(registry);
        });
    }
}
