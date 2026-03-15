package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyResolverTest {

    @Test
    void resolvesMatchingPolicies() {
        Policy loginPolicy = createPolicy("login", List.of("/api/auth/login"), List.of("POST"), 1);
        Policy paymentPolicy = createPolicy("payment", List.of("/api/payments/**"), List.of("POST"), 2);
        Policy getPolicy = createPolicy("get-all", List.of("/api/**"), List.of("GET"), 3);

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(loginPolicy, paymentPolicy, getPolicy));

        PolicyResolver resolver = new PolicyResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        List<Policy> result = resolver.resolve(request);

        assertEquals(1, result.size());
        assertEquals("login", result.get(0).getId());
    }

    @Test
    void resolvesMultiplePoliciesSortedByPriority() {
        Policy broad = createPolicy("broad", List.of("/api/**"), List.of("POST"), 10);
        Policy specific = createPolicy("specific", List.of("/api/payments/**"), List.of("POST"), 1);

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(broad, specific));

        PolicyResolver resolver = new PolicyResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/charge");
        List<Policy> result = resolver.resolve(request);

        assertEquals(2, result.size());
        assertEquals("specific", result.get(0).getId());
        assertEquals("broad", result.get(1).getId());
    }

    @Test
    void skipsDisabledPolicies() {
        Policy enabled = createPolicy("enabled", List.of("/api/**"), List.of("POST"), 1);
        Policy disabled = createPolicy("disabled", List.of("/api/**"), List.of("POST"), 2);
        disabled.setEnabled(false);

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(enabled, disabled));

        PolicyResolver resolver = new PolicyResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        List<Policy> result = resolver.resolve(request);

        assertEquals(1, result.size());
        assertEquals("enabled", result.get(0).getId());
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        Policy policy = createPolicy("login", List.of("/api/auth/login"), List.of("POST"), 1);

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(policy));

        PolicyResolver resolver = new PolicyResolver(props);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        List<Policy> result = resolver.resolve(request);

        assertTrue(result.isEmpty());
    }

    private Policy createPolicy(String id, List<String> paths, List<String> methods, int priority) {
        MatchCondition match = new MatchCondition();
        match.setPaths(paths);
        match.setMethods(methods);

        Policy policy = new Policy();
        policy.setId(id);
        policy.setEnabled(true);
        policy.setPriority(priority);
        policy.setMatch(match);
        policy.setSubjects(List.of("ip"));
        policy.setLimit(10);
        policy.setWindowSeconds(60);
        return policy;
    }
}
