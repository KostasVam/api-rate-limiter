package com.vamva.ratelimiter.engine;

import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.Policy;
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
 *   <li>Aggregates results — if <strong>any</strong> policy is exceeded, the request is rejected</li>
 * </ol>
 *
 * <p>When multiple policies are exceeded, the one with the shortest {@code retryAfter}
 * is returned. When all policies pass, the one with the lowest {@code remaining} count
 * is returned (tightest limit for headers).</p>
 */
@Slf4j
@Component
public class RateLimitEngine {

    private final RateLimiterProperties properties;
    private final PolicyResolver policyResolver;
    private final CompositeKeyBuilder keyBuilder;
    private final RateLimitBackend backend;

    public RateLimitEngine(RateLimiterProperties properties,
                           PolicyResolver policyResolver,
                           CompositeKeyBuilder keyBuilder,
                           RateLimitBackend backend) {
        this.properties = properties;
        this.policyResolver = policyResolver;
        this.keyBuilder = keyBuilder;
        this.backend = backend;
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
        List<RateLimitResult> results = new ArrayList<>();

        for (Policy policy : policies) {
            Optional<String> subjectKey = keyBuilder.buildKey(policy, request);
            if (subjectKey.isEmpty()) {
                log.debug("Skipping policy '{}' — subject could not be extracted for route={}",
                        policy.getId(), route);
                continue;
            }

            long windowStart = Instant.now().getEpochSecond() / policy.getWindowSeconds();
            String redisKey = String.format("rl:%s:%s:%d", policy.getId(), subjectKey.get(), windowStart);

            RateLimitResult result = backend.increment(redisKey, policy.getLimit(),
                    policy.getWindowSeconds(), policy.getId());
            results.add(result);

            log.info("policy_id={} subject={} route={} decision={} remaining={} retry_after={}",
                    policy.getId(),
                    subjectKey.get(),
                    route,
                    result.isAllowed() ? "ALLOW" : "REJECT",
                    result.getRemaining(),
                    result.getRetryAfterSeconds());
        }

        if (results.isEmpty()) {
            return Optional.empty();
        }

        // If any policy is exceeded, reject with shortest retry-after
        Optional<RateLimitResult> rejected = results.stream()
                .filter(r -> !r.isAllowed())
                .min(Comparator.comparingLong(RateLimitResult::getRetryAfterSeconds));

        if (rejected.isPresent()) {
            return rejected;
        }

        // All allowed — return the one with lowest remaining (tightest limit)
        return results.stream()
                .min(Comparator.comparingInt(RateLimitResult::getRemaining));
    }
}
