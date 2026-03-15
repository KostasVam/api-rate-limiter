package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed rate limit counter using a Lua script for atomic operations.
 *
 * <p>Executes the {@code fixed_window.lua} script which atomically increments
 * the counter and sets TTL on first access. When Redis is unavailable, the
 * behavior depends on the {@code failOpen} flag:</p>
 * <ul>
 *   <li>{@code failOpen=true} — allows the request and logs a warning</li>
 *   <li>{@code failOpen=false} — rejects the request</li>
 * </ul>
 */
@Slf4j
public class RedisBackend implements RateLimitBackend {

    private static final int TTL_BUFFER_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;
    private final boolean failOpen;
    private final RateLimitMetrics metrics;

    public RedisBackend(StringRedisTemplate redisTemplate, boolean failOpen, RateLimitMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.failOpen = failOpen;
        this.metrics = metrics;

        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/fixed_window.lua"));
        this.script.setResultType(List.class);
    }

    @Override
    public RateLimitResult increment(String key, int limit, int windowSeconds, String policyId) {
        try {
            int ttl = windowSeconds + TTL_BUFFER_SECONDS;
            List result = redisTemplate.execute(script,
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

    private RateLimitResult handleFailure(int limit, int windowSeconds, String policyId) {
        long resetEpoch = Instant.now().getEpochSecond() + windowSeconds;
        if (failOpen) {
            log.warn("Fail-open: allowing request due to backend error (policy={})", policyId);
            return RateLimitResult.allowed(limit, limit, resetEpoch, policyId);
        }
        return RateLimitResult.rejected(limit, resetEpoch, policyId, windowSeconds);
    }
}
