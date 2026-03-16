package com.vamva.ratelimiter.backend;

import com.vamva.ratelimiter.model.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
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
 *
 * <p><strong>Concurrency:</strong> Window-based algorithms use {@link java.util.concurrent.ConcurrentHashMap}
 * with {@link java.util.concurrent.atomic.AtomicInteger} for lock-free counting. Token bucket uses
 * per-bucket synchronization for atomic refill-and-consume. This backend prioritizes correctness
 * over maximum throughput — use {@link RedisBackend} for high-concurrency production workloads.</p>
 */
@Slf4j
public class InMemoryBackend implements RateLimitBackend {

    private final ConcurrentHashMap<String, WindowEntry> windows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Creates an InMemoryBackend using the system UTC clock. */
    public InMemoryBackend() {
        this(Clock.systemUTC());
    }

    /**
     * Creates an InMemoryBackend with the specified clock.
     *
     * @param clock the clock to use for time-based operations
     */
    public InMemoryBackend(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitResult increment(String key, int limit, int windowSeconds, String policyId) {
        long now = clock.instant().getEpochSecond();
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
        long now = clock.instant().getEpochSecond();
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

    @Override
    public RateLimitResult tokenBucketConsume(String key, int capacity, double refillRate, String policyId) {
        long now = clock.instant().getEpochSecond();

        BucketEntry bucket = buckets.computeIfAbsent(key,
                k -> new BucketEntry(capacity, now));

        // Synchronize per-bucket for atomic refill + consume
        synchronized (bucket) {
            long elapsed = now - bucket.lastRefill;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillRate);
            bucket.lastRefill = now;

            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                int remaining = (int) Math.floor(bucket.tokens);
                long resetEpoch = now + (long) Math.ceil(1.0 / refillRate);
                return RateLimitResult.allowed(capacity, remaining, resetEpoch, policyId);
            }

            double deficit = 1 - bucket.tokens;
            long retryAfter = (long) Math.ceil(deficit / refillRate);
            long resetEpoch = now + retryAfter;
            return RateLimitResult.rejected(capacity, resetEpoch, policyId, retryAfter);
        }
    }

    /**
     * Removes expired window entries and stale bucket entries to prevent memory leaks.
     * Buckets are considered stale if they haven't been accessed for more than 5 minutes.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = clock.instant().getEpochSecond();
        int removedWindows = 0;
        int removedBuckets = 0;

        var windowIterator = windows.entrySet().iterator();
        while (windowIterator.hasNext()) {
            if (windowIterator.next().getValue().expiresAt() < now) {
                windowIterator.remove();
                removedWindows++;
            }
        }

        long bucketStaleThreshold = now - 300; // 5 minutes
        var bucketIterator = buckets.entrySet().iterator();
        while (bucketIterator.hasNext()) {
            if (bucketIterator.next().getValue().lastRefill < bucketStaleThreshold) {
                bucketIterator.remove();
                removedBuckets++;
            }
        }

        if (removedWindows > 0 || removedBuckets > 0) {
            log.debug("Cleaned up {} expired windows and {} stale buckets", removedWindows, removedBuckets);
        }
    }

    record WindowEntry(AtomicInteger counter, long expiresAt) {}

    static class BucketEntry {
        double tokens;
        long lastRefill;

        BucketEntry(double tokens, long lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }
}
