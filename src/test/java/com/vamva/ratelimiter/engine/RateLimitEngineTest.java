package com.vamva.ratelimiter.engine;

import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.PolicyMode;
import com.vamva.ratelimiter.model.RateLimitResult;
import com.vamva.ratelimiter.policy.PolicyResolver;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import com.vamva.ratelimiter.subject.IpExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitEngineTest {

    private RateLimiterProperties properties;
    private RateLimitBackend backend;
    private RateLimitMetrics metrics;
    private RateLimitEngine engine;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);

        Policy policy = createPolicy("test-policy", List.of("/api/**"), List.of("POST"),
                List.of("ip"), PolicyMode.ENFORCE);
        properties.setPolicies(List.of(policy));

        PolicyResolver resolver = new PolicyResolver(properties);
        CompositeKeyBuilder keyBuilder = new CompositeKeyBuilder(List.of(new IpExtractor()));
        backend = mock(RateLimitBackend.class);
        metrics = mock(RateLimitMetrics.class);

        engine = new RateLimitEngine(properties, resolver, keyBuilder, backend, metrics);
    }

    @Test
    void returnsEmptyWhenDisabled() {
        properties.setEnabled(false);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        Optional<RateLimitResult> result = engine.evaluate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenNoPoliciesMatch() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        Optional<RateLimitResult> result = engine.evaluate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void returnsAllowedResult() {
        when(backend.increment(anyString(), eq(10), eq(60), eq("test-policy")))
                .thenReturn(RateLimitResult.allowed(10, 9, 1000L, "test-policy"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr("1.2.3.4");

        Optional<RateLimitResult> result = engine.evaluate(request);

        assertTrue(result.isPresent());
        assertTrue(result.get().isAllowed());
        assertEquals(9, result.get().getRemaining());
    }

    @Test
    void returnsRejectedResult() {
        when(backend.increment(anyString(), eq(10), eq(60), eq("test-policy")))
                .thenReturn(RateLimitResult.rejected(10, 1000L, "test-policy", 42));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr("1.2.3.4");

        Optional<RateLimitResult> result = engine.evaluate(request);

        assertTrue(result.isPresent());
        assertFalse(result.get().isAllowed());
        assertEquals(42, result.get().getRetryAfterSeconds());
    }

    @Test
    void observeModeDoesNotReject() {
        Policy observePolicy = createPolicy("observe-policy", List.of("/api/**"), List.of("POST"),
                List.of("ip"), PolicyMode.OBSERVE);
        properties.setPolicies(List.of(observePolicy));

        // Recreate engine with updated policies
        PolicyResolver resolver = new PolicyResolver(properties);
        CompositeKeyBuilder keyBuilder = new CompositeKeyBuilder(List.of(new IpExtractor()));
        engine = new RateLimitEngine(properties, resolver, keyBuilder, backend, metrics);

        when(backend.increment(anyString(), eq(10), eq(60), eq("observe-policy")))
                .thenReturn(RateLimitResult.rejected(10, 1000L, "observe-policy", 42));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr("1.2.3.4");

        Optional<RateLimitResult> result = engine.evaluate(request);

        // Should return empty — observe policies don't contribute to enforcement
        assertTrue(result.isEmpty());

        // But should record the observed rejection metric
        verify(metrics).recordObservedRejection(eq("observe-policy"), anyString());
    }

    @Test
    void observeModeCountersStillIncrement() {
        Policy observePolicy = createPolicy("observe-policy", List.of("/api/**"), List.of("POST"),
                List.of("ip"), PolicyMode.OBSERVE);
        properties.setPolicies(List.of(observePolicy));

        PolicyResolver resolver = new PolicyResolver(properties);
        CompositeKeyBuilder keyBuilder = new CompositeKeyBuilder(List.of(new IpExtractor()));
        engine = new RateLimitEngine(properties, resolver, keyBuilder, backend, metrics);

        when(backend.increment(anyString(), eq(10), eq(60), eq("observe-policy")))
                .thenReturn(RateLimitResult.allowed(10, 9, 1000L, "observe-policy"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        request.setRemoteAddr("1.2.3.4");

        engine.evaluate(request);

        // Backend should still be called (counters increment in observe mode)
        verify(backend).increment(anyString(), eq(10), eq(60), eq("observe-policy"));
    }

    private Policy createPolicy(String id, List<String> paths, List<String> methods,
                                List<String> subjects, PolicyMode mode) {
        MatchCondition match = new MatchCondition();
        match.setPaths(paths);
        match.setMethods(methods);

        Policy policy = new Policy();
        policy.setId(id);
        policy.setEnabled(true);
        policy.setMode(mode);
        policy.setPriority(1);
        policy.setMatch(match);
        policy.setSubjects(subjects);
        policy.setLimit(10);
        policy.setWindowSeconds(60);
        return policy;
    }
}
