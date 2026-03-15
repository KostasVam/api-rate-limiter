package com.vamva.ratelimiter.engine;

import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.PolicyMode;
import com.vamva.ratelimiter.model.RateLimitResult;
import com.vamva.ratelimiter.policy.CanonicalRoute;
import com.vamva.ratelimiter.policy.PolicyResolver;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core rate limiting engine that orchestrates YAML-based policy evaluation.
 *
 * <p>For each incoming request, the engine:</p>
 * <ol>
 *   <li>Resolves matching policies via {@link PolicyResolver}</li>
 *   <li>Builds composite subject keys via {@link CompositeKeyBuilder}</li>
 *   <li>Evaluates each policy via {@link PolicyEvaluator} (shared with annotation interceptor)</li>
 *   <li>Aggregates results — if <strong>any enforced</strong> policy is exceeded, the request is rejected</li>
 * </ol>
 *
 * <p>Policies in {@code observe} mode are fully evaluated (counters incremented, metrics/logs
 * recorded) but their results do not contribute to the reject decision.</p>
 */
@Slf4j
public class RateLimitEngine {

    private final RateLimiterProperties properties;
    private final PolicyResolver policyResolver;
    private final CompositeKeyBuilder keyBuilder;
    private final PolicyEvaluator policyEvaluator;
    private final RateLimitMetrics metrics;

    public RateLimitEngine(RateLimiterProperties properties,
                           PolicyResolver policyResolver,
                           CompositeKeyBuilder keyBuilder,
                           PolicyEvaluator policyEvaluator,
                           RateLimitMetrics metrics) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.keyBuilder = keyBuilder;
        this.policyEvaluator = policyEvaluator;
        this.metrics = metrics;
    }

    /**
     * Evaluates all matching rate limit policies for the given request.
     *
     * @param request the incoming HTTP request
     * @return the aggregate result, or empty if no policies apply or the limiter is disabled
     */
    public Optional<RateLimitResult> evaluate(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        List<Policy> policies = policyResolver.resolve(request);
        if (policies.isEmpty()) {
            return Optional.empty();
        }

        String route = CanonicalRoute.from(request).toLogString();
        List<RateLimitResult> enforcedResults = new ArrayList<>();

        for (Policy policy : policies) {
            Optional<String> subjectKey = keyBuilder.buildKey(policy, request);
            if (subjectKey.isEmpty()) {
                log.debug("Skipping policy '{}' — subject could not be extracted for route={}",
                        policy.getId(), route);
                continue;
            }

            RateLimitResult rawResult = policyEvaluator.evaluate(policy, subjectKey.get());

            // Enrich rejected results with policy's custom error configuration
            RateLimitResult result = rawResult;
            if (!rawResult.isAllowed()) {
                result = RateLimitResult.rejected(
                        rawResult.getLimit(), rawResult.getResetEpochSeconds(),
                        rawResult.getPolicyId(), rawResult.getRetryAfterSeconds(),
                        policy.getEffectiveErrorMessage(), policy.getEffectiveErrorStatusCode());
            }

            boolean isObserve = policy.getMode() == PolicyMode.OBSERVE;
            String decision = resolveDecisionLabel(result, isObserve);

            log.info("policy_id={} mode={} subject={} route={} decision={} remaining={} retry_after={}",
                    policy.getId(),
                    policy.getMode().name().toLowerCase(),
                    subjectKey.get(),
                    route,
                    decision,
                    result.getRemaining(),
                    result.getRetryAfterSeconds());

            if (isObserve) {
                if (!result.isAllowed()) {
                    metrics.recordObservedRejection(policy.getId());
                }
            } else {
                enforcedResults.add(result);
            }
        }

        if (enforcedResults.isEmpty()) {
            return Optional.empty();
        }

        Optional<RateLimitResult> rejected = enforcedResults.stream()
                .filter(r -> !r.isAllowed())
                .min(Comparator.comparingLong(RateLimitResult::getRetryAfterSeconds));

        if (rejected.isPresent()) {
            return rejected;
        }

        return enforcedResults.stream()
                .min(Comparator.comparingInt(RateLimitResult::getRemaining));
    }

    private String resolveDecisionLabel(RateLimitResult result, boolean isObserve) {
        if (isObserve) {
            return result.isAllowed() ? "OBSERVE_ALLOW" : "OBSERVE_WOULD_REJECT";
        }
        return result.isAllowed() ? "ALLOW" : "REJECT";
    }
}
