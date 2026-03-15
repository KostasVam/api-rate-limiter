package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    private final InMemoryBackend backend = new InMemoryBackend();

    @Test
    void allowsFirstRequest() {
        // Capacity=10, refill=0.167/sec (10 per 60s)
        RateLimitResult result = backend.tokenBucketConsume(
                "rl:tb:test:ip:1.1.1.1", 10, 0.167, "test");

        assertTrue(result.isAllowed());
        assertEquals(9, result.getRemaining()); // capacity - 1
    }

    @Test
    void allowsBurstUpToCapacity() {
        String key = "rl:tb:test:ip:2.2.2.2";
        int capacity = 5;
        double refillRate = 5.0 / 60; // 5 per minute

        for (int i = 0; i < 5; i++) {
            RateLimitResult result = backend.tokenBucketConsume(key, capacity, refillRate, "test");
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void rejectsWhenBucketEmpty() {
        String key = "rl:tb:test:ip:3.3.3.3";
        int capacity = 3;
        double refillRate = 3.0 / 60;

        // Exhaust bucket
        for (int i = 0; i < 3; i++) {
            backend.tokenBucketConsume(key, capacity, refillRate, "test");
        }

        // 4th request should be rejected
        RateLimitResult result = backend.tokenBucketConsume(key, capacity, refillRate, "test");
        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertTrue(result.getRetryAfterSeconds() > 0);
    }

    @Test
    void differentSubjectsAreIndependent() {
        int capacity = 2;
        double refillRate = 2.0 / 60;

        // Exhaust bucket A
        backend.tokenBucketConsume("rl:tb:test:ip:A", capacity, refillRate, "test");
        backend.tokenBucketConsume("rl:tb:test:ip:A", capacity, refillRate, "test");
        RateLimitResult blockedA = backend.tokenBucketConsume("rl:tb:test:ip:A", capacity, refillRate, "test");
        assertFalse(blockedA.isAllowed());

        // Bucket B should still be full
        RateLimitResult allowedB = backend.tokenBucketConsume("rl:tb:test:ip:B", capacity, refillRate, "test");
        assertTrue(allowedB.isAllowed());
    }

    @Test
    void returnsCorrectPolicyId() {
        RateLimitResult result = backend.tokenBucketConsume(
                "rl:tb:my-policy:ip:1.1.1.1", 10, 0.167, "my-policy");
        assertEquals("my-policy", result.getPolicyId());
    }
}
