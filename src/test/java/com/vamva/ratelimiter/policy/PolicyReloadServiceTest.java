package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyReloadServiceTest {

    @Test
    void loadsInitialPolicies() {
        RateLimiterProperties props = createProps(2);
        PolicyStore store = new YamlPolicyStore(props);
        PolicyReloadService service = new PolicyReloadService(store, props);

        assertEquals(2, service.getActivePolicies().size());
    }

    @Test
    void reload_refreshesPolicies() {
        RateLimiterProperties props = createProps(1);
        PolicyStore store = new YamlPolicyStore(props);
        PolicyReloadService service = new PolicyReloadService(store, props);

        assertEquals(1, service.getActivePolicies().size());

        // Simulate config change
        props.setPolicies(List.of(createPolicy("p1"), createPolicy("p2"), createPolicy("p3")));
        service.reload();

        assertEquals(3, service.getActivePolicies().size());
    }

    @Test
    void isEnabled_reflectsProperties() {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setEnabled(true);
        PolicyStore store = new YamlPolicyStore(props);
        PolicyReloadService service = new PolicyReloadService(store, props);

        assertTrue(service.isEnabled());

        props.setEnabled(false);
        assertFalse(service.isEnabled());
    }

    private RateLimiterProperties createProps(int count) {
        RateLimiterProperties props = new RateLimiterProperties();
        List<Policy> policies = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            policies.add(createPolicy("policy-" + i));
        }
        props.setPolicies(policies);
        return props;
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
