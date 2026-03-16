package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.policy.YamlPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PolicyReloadEndpointTest {

    private RateLimiterProperties properties;
    private PolicyReloadEndpoint endpoint;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setPolicies(List.of(createPolicy("test-policy")));

        PolicyReloadService service = new PolicyReloadService(
                new YamlPolicyStore(properties), properties);
        endpoint = new PolicyReloadEndpoint(service);
    }

    @Test
    void status_returnsEnabledAndPolicies() {
        Map<String, Object> status = endpoint.status();

        assertEquals(true, status.get("enabled"));
        assertEquals(1, status.get("policyCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies = (List<Map<String, Object>>) status.get("policies");
        assertEquals("test-policy", policies.get(0).get("id"));
    }

    @Test
    void reload_returnsCounts() {
        Map<String, Object> result = endpoint.reload();

        assertEquals("reloaded", result.get("status"));
        assertEquals(1, result.get("previousCount"));
        assertEquals(1, result.get("currentCount"));
    }

    @Test
    void reload_reflectsChanges() {
        properties.setPolicies(List.of(
                createPolicy("p1"), createPolicy("p2")));

        Map<String, Object> result = endpoint.reload();

        assertEquals(1, result.get("previousCount"));
        assertEquals(2, result.get("currentCount"));
    }

    private Policy createPolicy(String id) {
        Policy p = new Policy();
        p.setId(id);
        p.setEnabled(true);
        p.setLimit(10);
        p.setWindowSeconds(60);
        p.setSubjects(List.of("ip"));
        MatchCondition match = new MatchCondition();
        match.setPaths(List.of("/api/**"));
        p.setMatch(match);
        return p;
    }
}
