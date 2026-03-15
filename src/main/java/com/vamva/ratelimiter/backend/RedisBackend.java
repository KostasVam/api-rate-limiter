package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed rate limit counter using Lua scripts for atomic operations.
 *
 * <p>Supports two algorithms:</p>
 * <ul>
 *   <li><strong>Fixed Window</strong> — single INCR + EXPIRE per request</li>
 *   <li><strong>Sliding Window Counter</strong> — weighted average of current and previous window</li>
 * </ul>
 *
 * <p>When Redis is unavailable, behavior depends on the {@code failOpen} flag.</p>
 */
@Slf4j
public class RedisBackend implements RateLimitBackend {

    private static final int TTL_BUFFER_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final boolean failOpen;
    private final RateLimitMetrics metrics;

    public RedisBackend(StringRedisTemplate redisTemplate, boolean failOpen, RateLimitMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.failOpen = failOpen;
        this.metrics = metrics;

        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setLocation(new ClassPathResource("scripts/fixed_window.lua"));
        this.fixedWindowScript.setResultType(List.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
        this.slidingWindowScript.setResultType(List.class);
    }

    @Override
    public RateLimitResult increment(String key, int limit, int windowSeconds, String policyId) {
        try {
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;
            List result = redisTemplate.execute(fixedWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(ttl));

            long currentCount = ((Number) result.get(0)).longValue();
            long remainingTtl = ((Number) result.get(1)).longValue();

            int remaining = Math.max(0, (int) (limit - currentCount));
            long resetEpoch = Instant.now().getEpochSecond() + remainingTtl;

            if (currentCount > limit) {
                return RateLimitResult.rejected(limit, resetEpoch, policyId, remainingTtl);
            }

            return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);

        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed for key={}: {}", key, e.getMessage());
            metrics.recordBackendError();
            return handleFailure(limit, windowSeconds, policyId);

        } catch (Exception e) {
            log.error("Unexpected error during rate limit check for key={}: {}", key, e.getMessage(), e);
            metrics.recordBackendError();
            return handleFailure(limit, windowSeconds, policyId);
        }
    }

    @Override
    public RateLimitResult slidingWindowIncrement(String currentKey, String previousKey,
                                                   int limit, int windowSeconds,
                                                   double overlapWeight, String policyId) {
        try {
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;
            int weightPct = (int) (overlapWeight * 100);

            List result = redisTemplate.execute(slidingWindowScript,
                    Arrays.asList(currentKey, previousKey),
                    String.valueOf(ttl),
                    String.valueOf(weightPct));

            long currentCount = ((Number) result.get(0)).longValue();
            long previousCount = ((Number) result.get(1)).longValue();
            long remainingTtl = ((Number) result.get(2)).longValue();

            // Weighted count: current + (previous * overlap weight)
            double weightedCount = currentCount + (previousCount * overlapWeight);
            int effectiveCount = (int) Math.ceil(weightedCount);

            int remaining = Math.max(0, limit - effectiveCount);
            long resetEpoch = Instant.now().getEpochSecond() + remainingTtl;

            if (effectiveCount > limit) {
                return RateLimitResult.rejected(limit, resetEpoch, policyId, remainingTtl);
            }

            return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);

        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed for sliding window key={}: {}", currentKey, e.getMessage());
            metrics.recordBackendError();
            return handleFailure(limit, windowSeconds, policyId);

        } catch (Exception e) {
            log.error("Unexpected error during sliding window check for key={}: {}", currentKey, e.getMessage(), e);
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
}
