package com.vamva.ratelimiter.model;

/**
 * Defines the enforcement mode for a rate limiting policy.
 */
public enum PolicyMode {

    /**
     * Enforce mode: requests exceeding the limit are rejected with HTTP 429.
     */
    ENFORCE,

    /**
     * Observe mode (shadow mode): full evaluation occurs — counters are incremented,
     * metrics and logs are recorded — but requests are never rejected.
     *
     * <p>Use this mode to safely test new policies in production without impacting traffic.</p>
     */
    OBSERVE
}
