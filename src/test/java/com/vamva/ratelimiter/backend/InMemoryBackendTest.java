package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryBackendTest {

    private final InMemoryBackend backend = new InMemoryBackend();

    @Test
    void allowsRequestsWithinLimit() {
        String key = "rl:test:ip:1.2.3.4";
        int limit = 5;

        for (int i = 1; i <= 5; i++) {
            RateLimitResult result = backend.increment(key, limit, 60, "test");
            assertTrue(result.isAllowed(), "Request " + i + " should be allowed");
            assertEquals(limit - i, result.getRemaining());
        }
    }

    @Test
    void rejectsWhenLimitExceeded() {
        String key = "rl:test:ip:1.2.3.4";
        int limit = 3;

        for (int i = 0; i < 3; i++) {
            backend.increment(key, limit, 60, "test");
        }

        RateLimitResult result = backend.increment(key, limit, 60, "test");
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertTrue(result.getRetryAfterSeconds() > 0);
    }

    @Test
    void differentKeysAreIndependent() {
        RateLimitResult r1 = backend.increment("rl:test:ip:1.1.1.1", 2, 60, "test");
        RateLimitResult r2 = backend.increment("rl:test:ip:2.2.2.2", 2, 60, "test");

        assertTrue(r1.isAllowed());
        assertTrue(r2.isAllowed());
        assertEquals(1, r1.getRemaining());
        assertEquals(1, r2.getRemaining());
    }

    @Test
    void returnsCorrectPolicyId() {
        RateLimitResult result = backend.increment("rl:my-policy:ip:1.1.1.1", 10, 60, "my-policy");
        assertEquals("my-policy", result.getPolicyId());
    }
}
