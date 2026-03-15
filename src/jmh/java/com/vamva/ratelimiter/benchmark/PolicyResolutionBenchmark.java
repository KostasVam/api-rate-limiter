package com.vamva.ratelimiter.benchmark;

import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.policy.PolicyResolver;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks policy resolution performance with varying numbers of policies.
 * Measures the cost of matching, filtering, and sorting policies per request.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class PolicyResolutionBenchmark {

    private PolicyResolver resolver5;
    private PolicyResolver resolver20;
    private PolicyResolver resolver100;
    private MockHttpServletRequest matchingRequest;
    private MockHttpServletRequest nonMatchingRequest;

    @Setup(Level.Trial)
    public void setup() {
        resolver5 = createResolver(5);
        resolver20 = createResolver(20);
        resolver100 = createResolver(100);

        matchingRequest = new MockHttpServletRequest("POST", "/api/resource-3");
        nonMatchingRequest = new MockHttpServletRequest("DELETE", "/unknown/path");
    }

    @Benchmark
    public void resolve_5policies_match(Blackhole bh) {
        bh.consume(resolver5.resolve(matchingRequest));
    }

    @Benchmark
    public void resolve_20policies_match(Blackhole bh) {
        bh.consume(resolver20.resolve(matchingRequest));
    }

    @Benchmark
    public void resolve_100policies_match(Blackhole bh) {
        bh.consume(resolver100.resolve(matchingRequest));
    }

    @Benchmark
    public void resolve_100policies_noMatch(Blackhole bh) {
        bh.consume(resolver100.resolve(nonMatchingRequest));
    }

    private PolicyResolver createResolver(int count) {
        List<Policy> policies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Policy p = new Policy();
            p.setId("policy-" + i);
            p.setEnabled(true);
            p.setPriority(i);
            p.setLimit(100);
            p.setWindowSeconds(60);
            p.setSubjects(List.of("ip"));

            MatchCondition match = new MatchCondition();
            match.setPaths(List.of("/api/resource-" + i));
            match.setMethods(List.of("POST", "PUT"));
            p.setMatch(match);

            policies.add(p);
        }

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(policies);
        return new PolicyResolver(new PolicyReloadService(new com.vamva.ratelimiter.policy.YamlPolicyStore(props), props));
    }
}
