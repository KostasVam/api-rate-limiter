package com.vamva.ratelimiter.model;

import lombok.Getter;

/**
 * Immutable result of a rate limit evaluation for a single policy.
 *
 * <p>Contains the decision (allowed/rejected) along with metadata used
 * for HTTP response headers, custom error rendering, and logging.</p>
 */
@Getter
public class RateLimitResult {

    private final boolean allowed;
    private final int limit;
    private final int remaining;
    private final long resetEpochSeconds;
    private final String policyId;
    private final long retryAfterSeconds;
    private final String errorMessage;
    private final int errorStatusCode;

    /**
     * Constructs a new rate limit result with the given parameters.
     *
     * @param allowed            whether the request is allowed
     * @param limit              the policy limit
     * @param remaining          remaining requests in the current window
     * @param resetEpochSeconds  epoch second when the window resets
     * @param policyId           the ID of the evaluated policy
     * @param retryAfterSeconds  seconds until the client may retry
     * @param errorMessage       the error message for rejected requests
     * @param errorStatusCode    the HTTP status code for rejected requests
     */
    public RateLimitResult(boolean allowed, int limit, int remaining,
                           long resetEpochSeconds, String policyId, long retryAfterSeconds,
                           String errorMessage, int errorStatusCode) {
        this.allowed = allowed;
        this.limit = limit;
        this.remaining = remaining;
        this.resetEpochSeconds = resetEpochSeconds;
        this.policyId = policyId;
        this.retryAfterSeconds = retryAfterSeconds;
        this.errorMessage = errorMessage;
        this.errorStatusCode = errorStatusCode;
    }

    /**
     * Creates an allowed result.
     *
     * @param limit              the policy limit
     * @param remaining          remaining requests in the current window
     * @param resetEpochSeconds  epoch second when the window resets
     * @param policyId           the ID of the evaluated policy
     * @return an allowed rate limit result
     */
    public static RateLimitResult allowed(int limit, int remaining, long resetEpochSeconds, String policyId) {
        return new RateLimitResult(true, limit, remaining, resetEpochSeconds, policyId, 0,
                "Too many requests", 429);
    }

    /**
     * Creates a rejected result with default error message and status code.
     *
     * @param limit              the policy limit
     * @param resetEpochSeconds  epoch second when the window resets
     * @param policyId           the ID of the evaluated policy
     * @param retryAfterSeconds  seconds until the client may retry
     * @return a rejected rate limit result
     */
    public static RateLimitResult rejected(int limit, long resetEpochSeconds, String policyId, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, resetEpochSeconds, policyId, retryAfterSeconds,
                "Too many requests", 429);
    }

    /**
     * Creates a rejected result with a custom error message and status code.
     *
     * @param limit              the policy limit
     * @param resetEpochSeconds  epoch second when the window resets
     * @param policyId           the ID of the evaluated policy
     * @param retryAfterSeconds  seconds until the client may retry
     * @param errorMessage       the custom error message
     * @param errorStatusCode    the custom HTTP status code
     * @return a rejected rate limit result
     */
    public static RateLimitResult rejected(int limit, long resetEpochSeconds, String policyId,
                                           long retryAfterSeconds, String errorMessage, int errorStatusCode) {
        return new RateLimitResult(false, limit, 0, resetEpochSeconds, policyId, retryAfterSeconds,
                errorMessage, errorStatusCode);
    }
}
