package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;

/**
 * Storage backend for rate limit counters.
 *
 * <p>Implementations handle atomic counter increments within time windows.
 * Two implementations are provided: {@link RedisBackend} for distributed
 * deployments and {@link InMemoryBackend} for local/dev use.</p>
 */
public interface RateLimitBackend {

    /**
     * Atomically increments the fixed window counter and returns the result.
     *
     * @param key           the fully-qualified rate limit key (e.g., {@code rl:policy:subject:window})
     * @param limit         the maximum allowed requests in the window
     * @param windowSeconds the window duration in seconds
     * @param policyId      the policy identifier (included in the result for metrics/logging)
     * @return the rate limit evaluation result
     */
    RateLimitResult increment(String key, int limit, int windowSeconds, String policyId);

    /**
     * Evaluates a sliding window counter using weighted average of current and previous windows.
     *
     * <p>The sliding window counter smooths out boundary spikes by computing:
     * {@code weighted_count = current_count + (previous_count * overlap_weight)}</p>
     *
     * @param currentKey    the key for the current window
     * @param previousKey   the key for the previous window
     * @param limit         the maximum allowed requests
     * @param windowSeconds the window duration in seconds
     * @param overlapWeight weight of the previous window (0.0 to 1.0)
     * @param policyId      the policy identifier
     * @return the rate limit evaluation result
     */
    RateLimitResult slidingWindowIncrement(String currentKey, String previousKey,
                                           int limit, int windowSeconds,
                                           double overlapWeight, String policyId);

    /**
     * Evaluates a token bucket: refills tokens at a steady rate, consumes one per request.
     *
     * <p>Allows controlled bursts up to {@code capacity} while enforcing a long-term
     * average rate of {@code refillRate} tokens per second.</p>
     *
     * @param key          the bucket key
     * @param capacity     maximum tokens the bucket can hold (burst capacity)
     * @param refillRate   tokens added per second
     * @param policyId     the policy identifier
     * @return the rate limit evaluation result
     */
    RateLimitResult tokenBucketConsume(String key, int capacity, double refillRate, String policyId);
}
