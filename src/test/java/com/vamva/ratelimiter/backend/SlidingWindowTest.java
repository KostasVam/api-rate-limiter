package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowTest {

    private final InMemoryBackend backend = new InMemoryBackend();

    @Test
    void allowsWithinLimit_noHistory() {
        RateLimitResult result = backend.slidingWindowIncrement(
                "rl:test:ip:1.1.1.1:100", "rl:test:ip:1.1.1.1:99",
                5, 60, 0.5, "test");

        assertTrue(result.isAllowed());
        // Current=1, previous=0, weighted=1 → remaining=4
        assertEquals(4, result.getRemaining());
    }

    @Test
    void rejectsWhenWeightedCountExceedsLimit() {
        // Simulate previous window with 8 requests using slidingWindowIncrement
        // to populate the "previous" key with a known count
        String prevKey = "rl:test:ip:1.1.1.1:99";
        String currKey = "rl:test:ip:1.1.1.1:100";
        String olderKey = "rl:test:ip:1.1.1.1:98";

        // Fill previous window by calling slidingWindowIncrement with prevKey as current
        for (int i = 0; i < 8; i++) {
            backend.slidingWindowIncrement(prevKey, olderKey, 100, 60, 0.0, "test");
        }

        // Now use currKey as current, prevKey as previous
        // First request: current=1, prev=8, weight=0.5 → weighted=1+(8*0.5)=5 → allowed (limit=5)
        RateLimitResult r1 = backend.slidingWindowIncrement(currKey, prevKey, 5, 60, 0.5, "test");
        assertTrue(r1.isAllowed());

        // Second request: current=2, prev=8, weight=0.5 → weighted=2+(8*0.5)=6 → rejected
        RateLimitResult r2 = backend.slidingWindowIncrement(currKey, prevKey, 5, 60, 0.5, "test");
        assertFalse(r2.isAllowed());
    }

    @Test
    void previousWindowFullyExpired_noImpact() {
        String currKey = "rl:test:ip:2.2.2.2:100";
        String prevKey = "rl:test:ip:2.2.2.2:99";
        String olderKey = "rl:test:ip:2.2.2.2:98";

        // Fill previous window with 100 requests
        for (int i = 0; i < 100; i++) {
            backend.slidingWindowIncrement(prevKey, olderKey, 200, 60, 0.0, "test");
        }

        // With weight=0.0, only current window counts
        RateLimitResult result = backend.slidingWindowIncrement(currKey, prevKey, 5, 60, 0.0, "test");
        assertTrue(result.isAllowed());
        assertEquals(4, result.getRemaining()); // current=1, weighted_prev=0
    }

    @Test
    void differentSubjectsAreIndependent() {
        RateLimitResult r1 = backend.slidingWindowIncrement(
                "rl:test:ip:A:100", "rl:test:ip:A:99", 5, 60, 0.5, "test");
        RateLimitResult r2 = backend.slidingWindowIncrement(
                "rl:test:ip:B:100", "rl:test:ip:B:99", 5, 60, 0.5, "test");

        assertTrue(r1.isAllowed());
        assertTrue(r2.isAllowed());
        assertEquals(4, r1.getRemaining());
        assertEquals(4, r2.getRemaining());
    }
}
