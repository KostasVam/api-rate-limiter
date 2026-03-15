package com.vamva.ratelimiter.model;

/**
 * Rate limiting algorithm used by a policy.
 */
public enum Algorithm {

    /**
     * Fixed Window Counter: requests counted in discrete time windows.
     * Simple and fast, but susceptible to boundary spikes.
     */
    FIXED_WINDOW,

    /**
     * Sliding Window Counter: weighted average of current and previous window counters.
     * Smooths out boundary spikes with minimal additional cost (2 Redis keys instead of 1).
     */
    SLIDING_WINDOW
}
