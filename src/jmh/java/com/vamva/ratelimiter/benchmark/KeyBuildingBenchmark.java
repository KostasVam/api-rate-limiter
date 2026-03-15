package com.vamva.ratelimiter.benchmark;

import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.subject.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks subject key building with varying numbers of extractors.
 * Measures the overhead of subject extraction and composite key construction.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class KeyBuildingBenchmark {

    private CompositeKeyBuilder builder;
    private MockHttpServletRequest request;
    private Policy singleSubjectPolicy;
    private Policy tripleSubjectPolicy;

    @Setup(Level.Trial)
    public void setup() {
        builder = new CompositeKeyBuilder(List.of(
                new IpExtractor(),
                new UserExtractor(),
                new ApiKeyExtractor(),
                new TenantExtractor(),
                new RouteExtractor()
        ));

        request = new MockHttpServletRequest("POST", "/api/payments");
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-API-Key", "client-abc-123");
        request.addHeader("X-User-Id", "user-42");

        singleSubjectPolicy = new Policy();
        singleSubjectPolicy.setId("single");
        singleSubjectPolicy.setSubjects(List.of("ip"));

        tripleSubjectPolicy = new Policy();
        tripleSubjectPolicy.setId("triple");
        tripleSubjectPolicy.setSubjects(List.of("ip", "user", "route"));
    }

    @Benchmark
    public void singleSubject(Blackhole bh) {
        bh.consume(builder.buildKey(singleSubjectPolicy, request));
    }

    @Benchmark
    public void tripleSubject(Blackhole bh) {
        bh.consume(builder.buildKey(tripleSubjectPolicy, request));
    }
}
