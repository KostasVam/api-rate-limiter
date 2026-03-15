package com.vamva.ratelimiter.engine;

import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.Algorithm;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.PolicyMode;
import com.vamva.ratelimiter.model.RateLimitResult;
import com.vamva.ratelimiter.policy.PolicyResolver;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Core rate limiting engine that orchestrates policy evaluation.
 *
 * <p>For each incoming request, the engine:</p>
 * <ol>
 *   <li>Resolves matching policies via {@link PolicyResolver}</li>
 *   <li>Builds composite subject keys via {@link CompositeKeyBuilder}</li>
 *   <li>Increments counters via {@link RateLimitBackend}</li>
 *   <li>Aggregates results — if <strong>any enforced</strong> policy is exceeded, the request is rejected</li>
 * </ol>
 *
 * <p>Policies in {@code observe} mode are fully evaluated (counters incremented, metrics/logs
 * recorded) but their results do not contribute to the reject decision.</p>
 *
 * <p>When multiple enforced policies are exceeded, the one with the shortest {@code retryAfter}
 * is returned. When all enforced policies pass, the one with the lowest {@code remaining} count
 * is returned (most restrictive matched policy for response headers).</p>
 */
@Slf4j
@Component
public class RateLimitEngine {

    private final RateLimiterProperties properties;
    private final PolicyResolver policyResolver;
    private final CompositeKeyBuilder keyBuilder;
    private final RateLimitBackend backend;
    private final RateLimitMetrics metrics;

    public RateLimitEngine(RateLimiterProperties properties,
                           PolicyResolver policyResolver,
                           CompositeKeyBuilder keyBuilder,
                           RateLimitBackend backend,
                           RateLimitMetrics metrics) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.keyBuilder = keyBuilder;
        this.backend = backend;
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

        String route = request.getMethod() + " " + request.getRequestURI();
        List<RateLimitResult> enforcedResults = new ArrayList<>();

        for (Policy policy : policies) {
            Optional<String> subjectKey = keyBuilder.buildKey(policy, request);
            if (subjectKey.isEmpty()) {
                log.debug("Skipping policy '{}' — subject could not be extracted for route={}",
                        policy.getId(), route);
                continue;
            }

            RateLimitResult result = evaluatePolicy(policy, subjectKey.get());

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
                // Shadow mode: record what would have happened, but don't contribute to rejection
                if (!result.isAllowed()) {
                    metrics.recordObservedRejection(policy.getId(), route);
                }
            } else {
                enforcedResults.add(result);
            }
        }

        if (enforcedResults.isEmpty()) {
            return Optional.empty();
        }

        // If any enforced policy is exceeded, reject with shortest retry-after
        Optional<RateLimitResult> rejected = enforcedResults.stream()
                .filter(r -> !r.isAllowed())
                .min(Comparator.comparingLong(RateLimitResult::getRetryAfterSeconds));

        if (rejected.isPresent()) {
            return rejected;
        }

        // All enforced policies allowed — return most restrictive (lowest remaining)
        return enforcedResults.stream()
                .min(Comparator.comparingInt(RateLimitResult::getRemaining));
    }

    /**
     * Evaluates a single policy using the configured algorithm.
     *
     * <p>Keys use Redis hash tags {@code {policy:subject}} to ensure all keys for the same
     * policy+subject land on the same hash slot in Redis Cluster mode. This is required
     * for multi-key Lua scripts (sliding window) to work correctly.</p>
     */
    private RateLimitResult evaluatePolicy(Policy policy, String subjectKey) {
        long now = Instant.now().getEpochSecond();
        // Hash tag ensures all keys for same policy+subject go to same Redis Cluster slot
        String hashTag = String.format("{%s:%s}", policy.getId(), subjectKey);

        if (policy.getAlgorithm() == Algorithm.TOKEN_BUCKET) {
            String bucketKey = String.format("rl:tb:%s", hashTag);
            int capacity = policy.getEffectiveBurstCapacity();
            double refillRate = (double) policy.getLimit() / policy.getWindowSeconds();
            return backend.tokenBucketConsume(bucketKey, capacity, refillRate, policy.getId());
        }

        long windowStart = now / policy.getWindowSeconds();

        if (policy.getAlgorithm() == Algorithm.SLIDING_WINDOW) {
            long previousWindowStart = windowStart - 1;
            String currentKey = String.format("rl:%s:%d", hashTag, windowStart);
            String previousKey = String.format("rl:%s:%d", hashTag, previousWindowStart);

            long windowStartEpoch = windowStart * policy.getWindowSeconds();
            double elapsed = now - windowStartEpoch;
            double overlapWeight = 1.0 - (elapsed / policy.getWindowSeconds());

            return backend.slidingWindowIncrement(currentKey, previousKey,
                    policy.getLimit(), policy.getWindowSeconds(), overlapWeight, policy.getId());
        }

        // Default: fixed window
        String redisKey = String.format("rl:%s:%d", hashTag, windowStart);
        return backend.increment(redisKey, policy.getLimit(), policy.getWindowSeconds(), policy.getId());
    }

    private String resolveDecisionLabel(RateLimitResult result, boolean isObserve) {
        if (isObserve) {
            return result.isAllowed() ? "OBSERVE_ALLOW" : "OBSERVE_WOULD_REJECT";
        }
        return result.isAllowed() ? "ALLOW" : "REJECT";
    }
}
