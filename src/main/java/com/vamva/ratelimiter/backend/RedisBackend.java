package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Redis-backed rate limit counter using Lua scripts for atomic operations.
 *
 * <p>Supports three algorithms: Fixed Window, Sliding Window Counter, and Token Bucket.</p>
 *
 * <p>Wraps all Redis calls in a Resilience4j {@link CircuitBreaker} to prevent
 * cascading failures when Redis is unavailable. The circuit breaker transitions:</p>
 * <ul>
 *   <li><strong>CLOSED</strong> → normal operation, calls go through</li>
 *   <li><strong>OPEN</strong> → after failure threshold, calls short-circuit to fail-open/closed</li>
 *   <li><strong>HALF_OPEN</strong> → after wait duration, allows probe calls to test recovery</li>
 * </ul>
 */
@Slf4j
public class RedisBackend implements RateLimitBackend {

    private static final int TTL_BUFFER_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final boolean failOpen;
    private final RateLimitMetrics metrics;
    private final CircuitBreaker circuitBreaker;

    public RedisBackend(StringRedisTemplate redisTemplate, boolean failOpen, RateLimitMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.failOpen = failOpen;
        this.metrics = metrics;

        this.fixedWindowScript = loadScript("scripts/fixed_window.lua");
        this.slidingWindowScript = loadScript("scripts/sliding_window.lua");
        this.tokenBucketScript = loadScript("scripts/token_bucket.lua");

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        this.circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("redis-rate-limiter");

        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Circuit breaker state transition: {}", event));
    }

    @Override
    public RateLimitResult increment(String key, int limit, int windowSeconds, String policyId) {
        return executeWithCircuitBreaker(() -> {
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;
            List result = redisTemplate.execute(fixedWindowScript,
                    Collections.singletonList(key), String.valueOf(ttl));

            long currentCount = ((Number) result.get(0)).longValue();
            long remainingTtl = ((Number) result.get(1)).longValue();

            int remaining = Math.max(0, (int) (limit - currentCount));
            long resetEpoch = Instant.now().getEpochSecond() + remainingTtl;

            if (currentCount > limit) {
                return RateLimitResult.rejected(limit, resetEpoch, policyId, remainingTtl);
            }
            return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);
        }, limit, windowSeconds, policyId);
    }

    @Override
    public RateLimitResult slidingWindowIncrement(String currentKey, String previousKey,
                                                   int limit, int windowSeconds,
                                                   double overlapWeight, String policyId) {
        return executeWithCircuitBreaker(() -> {
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;
            int weightPct = (int) (overlapWeight * 100);

            List result = redisTemplate.execute(slidingWindowScript,
                    Arrays.asList(currentKey, previousKey),
                    String.valueOf(ttl), String.valueOf(weightPct));

            long currentCount = ((Number) result.get(0)).longValue();
            long previousCount = ((Number) result.get(1)).longValue();
            long remainingTtl = ((Number) result.get(2)).longValue();

            double weightedCount = currentCount + (previousCount * overlapWeight);
            int effectiveCount = (int) Math.ceil(weightedCount);

            int remaining = Math.max(0, limit - effectiveCount);
            long resetEpoch = Instant.now().getEpochSecond() + remainingTtl;

            if (effectiveCount > limit) {
                return RateLimitResult.rejected(limit, resetEpoch, policyId, remainingTtl);
            }
            return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);
        }, limit, windowSeconds, policyId);
    }

    @Override
    public RateLimitResult tokenBucketConsume(String key, int capacity, double refillRate, String policyId) {
        int windowSeconds = (int) Math.ceil(capacity / refillRate);

        return executeWithCircuitBreaker(() -> {
            long now = Instant.now().getEpochSecond();
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;

            List result = redisTemplate.execute(tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(now),
                    String.valueOf(ttl));

            long allowed = ((Number) result.get(0)).longValue();
            long remaining = ((Number) result.get(1)).longValue();
            long retryAfter = ((Number) result.get(2)).longValue();

            long resetEpoch = now + (retryAfter > 0 ? retryAfter : (long) Math.ceil(1.0 / refillRate));

            if (allowed == 1) {
                return RateLimitResult.allowed(capacity, (int) remaining, resetEpoch, policyId);
            }
            return RateLimitResult.rejected(capacity, resetEpoch, policyId, retryAfter);
        }, capacity, windowSeconds, policyId);
    }

    /**
     * Executes a Redis operation through the circuit breaker.
     * On failure (or when circuit is open), falls back to fail-open/closed behavior.
     */
    private RateLimitResult executeWithCircuitBreaker(Supplier<RateLimitResult> operation,
                                                       int limit, int windowSeconds, String policyId) {
        try {
            return circuitBreaker.executeSupplier(operation);
        } catch (Exception e) {
            log.error("Redis call failed (circuit={}): {}", circuitBreaker.getState(), e.getMessage());
            metrics.recordBackendError();
            return handleFailure(limit, windowSeconds, policyId);
        }
    }

    private RateLimitResult handleFailure(int limit, int windowSeconds, String policyId) {
        long resetEpoch = Instant.now().getEpochSecond() + windowSeconds;
        if (failOpen) {
            log.warn("Fail-open: allowing request due to backend error (policy={})", policyId);
            return RateLimitResult.allowed(limit, limit, resetEpoch, policyId);
        }
        return RateLimitResult.rejected(limit, resetEpoch, policyId, windowSeconds);
    }

    private DefaultRedisScript<List> loadScript(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
