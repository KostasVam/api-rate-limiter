package com.vamva.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable result of a rate limit evaluation for a single policy.
 *
 * <p>Contains the decision (allowed/rejected) along with metadata used
 * for HTTP response headers and logging.</p>
 */
@Getter
@AllArgsConstructor
public class RateLimitResult {

    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetEpochSeconds;
    private final String policyId;
    private final long retryAfterSeconds;

    /**
     * Creates an allowed result with the given remaining capacity.
     *
     * @param limit             the policy's request limit
     * @param remaining         remaining requests in the current window
     * @param resetEpochSeconds epoch second when the current window resets
     * @param policyId          the policy that was evaluated
     * @return an allowed {@link RateLimitResult}
     */
    public static RateLimitResult allowed(int limit, int remaining, long resetEpochSeconds, String policyId) {
        return new RateLimitResult(true, limit, remaining, resetEpochSeconds, policyId, 0);
    }

    /**
     * Creates a rejected result indicating the rate limit has been exceeded.
     *
     * @param limit             the policy's request limit
     * @param resetEpochSeconds epoch second when the current window resets
     * @param policyId          the policy that was exceeded
     * @param retryAfterSeconds seconds until the client can retry
     * @return a rejected {@link RateLimitResult}
     */
    public static RateLimitResult rejected(int limit, long resetEpochSeconds, String policyId, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, resetEpochSeconds, policyId, retryAfterSeconds);
    }
}
