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
     * Atomically increments the counter for the given key and returns the result.
     *
     * @param key           the fully-qualified rate limit key (e.g., {@code rl:policy:subject:window})
     * @param limit         the maximum allowed requests in the window
     * @param windowSeconds the window duration in seconds
     * @param policyId      the policy identifier (included in the result for metrics/logging)
     * @return the rate limit evaluation result
     */
    RateLimitResult increment(String key, int limit, int windowSeconds, String policyId);
}
