package com.vamva.ratelimiter.benchmark;

import com.vamva.ratelimiter.backend.InMemoryBackend;
import com.vamva.ratelimiter.model.RateLimitResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Microbenchmarks comparing throughput of all three rate limiting algorithms.
 *
 * <p>Uses the in-memory backend to isolate algorithm performance from
 * network/Redis latency. Each benchmark measures the cost of a single
 * increment/consume operation.</p>
 *
 * <p>Run with: {@code ./gradlew jmh}</p>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class AlgorithmBenchmark {

    private InMemoryBackend backend;
    private AtomicLong keyCounter;

    @Setup(Level.Trial)
    public void setup() {
        backend = new InMemoryBackend();
        keyCounter = new AtomicLong(0);
    }

    @Benchmark
    public void fixedWindow(Blackhole bh) {
        String key = "rl:bench:fw:" + keyCounter.incrementAndGet();
        RateLimitResult result = backend.increment(key, 1000, 60, "bench-fw");
        bh.consume(result);
    }

    @Benchmark
    public void fixedWindow_sameKey(Blackhole bh) {
        RateLimitResult result = backend.increment("rl:bench:fw:same", 1_000_000, 60, "bench-fw");
        bh.consume(result);
    }

    @Benchmark
    public void slidingWindow(Blackhole bh) {
        String key = "rl:bench:sw:" + keyCounter.incrementAndGet();
        RateLimitResult result = backend.slidingWindowIncrement(
                key, key + ":prev", 1000, 60, 0.5, "bench-sw");
        bh.consume(result);
    }

    @Benchmark
    public void slidingWindow_sameKey(Blackhole bh) {
        RateLimitResult result = backend.slidingWindowIncrement(
                "rl:bench:sw:same", "rl:bench:sw:same:prev", 1_000_000, 60, 0.5, "bench-sw");
        bh.consume(result);
    }

    @Benchmark
    public void tokenBucket(Blackhole bh) {
        String key = "rl:bench:tb:" + keyCounter.incrementAndGet();
        RateLimitResult result = backend.tokenBucketConsume(key, 1000, 16.67, "bench-tb");
        bh.consume(result);
    }

    @Benchmark
    public void tokenBucket_sameKey(Blackhole bh) {
        RateLimitResult result = backend.tokenBucketConsume(
                "rl:bench:tb:same", 1_000_000, 16667.0, "bench-tb");
        bh.consume(result);
    }
}
