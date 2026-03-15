package com.vamva.ratelimiter.engine;

import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.model.Algorithm;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.RateLimitResult;

import java.time.Clock;

/**
 * Evaluates a single policy against the backend using the configured algorithm.
 *
 * <p>This is the shared execution path for both YAML-based policies (via {@link RateLimitEngine})
 * and annotation-based policies (via {@link com.vamva.ratelimiter.annotation.RateLimitInterceptor}).
 * Centralizing this logic prevents behavior divergence between the two policy sources.</p>
 *
 * <p>Uses an injected {@link Clock} for all time operations, enabling deterministic
 * testing and future simulation mode.</p>
 */
public class PolicyEvaluator {

    private final RateLimitBackend backend;
    private final Clock clock;

    public PolicyEvaluator(RateLimitBackend backend, Clock clock) {
        this.backend = backend;
        this.clock = clock;
    }

    /**
     * Evaluates a single policy for the given subject key.
     *
     * @param policy     the policy to evaluate
     * @param subjectKey the composite subject key (e.g., "ip:1.2.3.4|route:POST:/api/payments")
     * @return the rate limit result
     */
    public RateLimitResult evaluate(Policy policy, String subjectKey) {
        long now = clock.instant().getEpochSecond();
        String hashTag = String.format("{%s:%s}", policy.getId(), subjectKey);

        if (policy.getAlgorithm() == Algorithm.TOKEN_BUCKET) {
            String bucketKey = String.format("rl:tb:%s", hashTag);
            int capacity = policy.getEffectiveBurstCapacity();
            double refillRate = (double) policy.getLimit() / policy.getWindowSeconds();
            return backend.tokenBucketConsume(bucketKey, capacity, refillRate, policy.getId());
        }

        long windowStart = now / policy.getWindowSeconds();

        if (policy.getAlgorithm() == Algorithm.SLIDING_WINDOW) {
            String currentKey = String.format("rl:%s:%d", hashTag, windowStart);
            String previousKey = String.format("rl:%s:%d", hashTag, windowStart - 1);

            long windowStartEpoch = windowStart * policy.getWindowSeconds();
            double elapsed = now - windowStartEpoch;
            double overlapWeight = 1.0 - (elapsed / policy.getWindowSeconds());

            return backend.slidingWindowIncrement(currentKey, previousKey,
                    policy.getLimit(), policy.getWindowSeconds(), overlapWeight, policy.getId());
        }

        String redisKey = String.format("rl:%s:%d", hashTag, windowStart);
        return backend.increment(redisKey, policy.getLimit(), policy.getWindowSeconds(), policy.getId());
    }
}
