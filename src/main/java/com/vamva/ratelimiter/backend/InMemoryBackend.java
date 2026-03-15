package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limit backend for single-instance and development use.
 *
 * <p>Supports both fixed window and sliding window counter algorithms.
 * Stores counters in a {@link ConcurrentHashMap} keyed by window.
 * Expired entries are cleaned up every 60 seconds via a scheduled task.</p>
 *
 * <p><strong>Warning:</strong> Counters are not shared across application instances.
 * Use {@link RedisBackend} for distributed deployments.</p>
 */
@Slf4j
public class InMemoryBackend implements RateLimitBackend {

    private final ConcurrentHashMap<String, WindowEntry> windows = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult increment(String key, int limit, int windowSeconds, String policyId) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        long resetEpoch = windowStart + windowSeconds;

        String windowKey = key + ":" + windowStart;

        WindowEntry entry = windows.computeIfAbsent(windowKey,
                k -> new WindowEntry(new AtomicInteger(0), resetEpoch));

        int current = entry.counter().incrementAndGet();
        int remaining = Math.max(0, limit - current);
        long retryAfter = resetEpoch - now;

        if (current > limit) {
            return RateLimitResult.rejected(limit, resetEpoch, policyId, retryAfter);
        }

        return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);
    }

    @Override
    public RateLimitResult slidingWindowIncrement(String currentKey, String previousKey,
                                                   int limit, int windowSeconds,
                                                   double overlapWeight, String policyId) {
        long now = Instant.now().getEpochSecond();
        long windowStart = (now / windowSeconds) * windowSeconds;
        long resetEpoch = windowStart + windowSeconds;

        // Increment current window
        WindowEntry currentEntry = windows.computeIfAbsent(currentKey,
                k -> new WindowEntry(new AtomicInteger(0), resetEpoch));
        int currentCount = currentEntry.counter().incrementAndGet();

        // Read previous window (may not exist)
        WindowEntry previousEntry = windows.get(previousKey);
        int previousCount = previousEntry != null ? previousEntry.counter().get() : 0;

        // Weighted count
        double weightedCount = currentCount + (previousCount * overlapWeight);
        int effectiveCount = (int) Math.ceil(weightedCount);

        int remaining = Math.max(0, limit - effectiveCount);
        long retryAfter = resetEpoch - now;

        if (effectiveCount > limit) {
            return RateLimitResult.rejected(limit, resetEpoch, policyId, retryAfter);
        }

        return RateLimitResult.allowed(limit, remaining, resetEpoch, policyId);
    }

    /** Removes expired window entries to prevent memory leaks. */
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = Instant.now().getEpochSecond();
        int removed = 0;
        var iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt() < now) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} expired rate limit windows", removed);
        }
    }

    record WindowEntry(AtomicInteger counter, long expiresAt) {}
}
