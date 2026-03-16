package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.Algorithm;
import com.vamva.ratelimiter.model.MatchCondition;
import com.vamva.ratelimiter.model.Policy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlPolicyStoreTest {

    @Test
    void loadsPoliciesOnConstruction() {
        RateLimiterProperties props = createProps("test-policy");
        YamlPolicyStore store = new YamlPolicyStore(props);

        assertEquals(1, store.getPolicies().size());
        assertEquals("test-policy", store.getPolicies().get(0).getId());
    }

    @Test
    void policiesAreImmutable() {
        RateLimiterProperties props = createProps("test-policy");
        YamlPolicyStore store = new YamlPolicyStore(props);

        assertThrows(UnsupportedOperationException.class,
                () -> store.getPolicies().add(new Policy()));
    }

    @Test
    void reload_refreshesPolicies() {
        RateLimiterProperties props = createProps("initial");
        YamlPolicyStore store = new YamlPolicyStore(props);

        assertEquals("initial", store.getPolicies().get(0).getId());

        props.setPolicies(List.of(createPolicy("reloaded")));
        store.reload();

        assertEquals("reloaded", store.getPolicies().get(0).getId());
    }

    @Test
    void rejectsInvalidPolicyOnLoad() {
        Policy invalid = new Policy();
        invalid.setId("bad-policy");
        invalid.setLimit(10);
        invalid.setWindowSeconds(60);
        invalid.setSubjects(List.of("ip"));
        invalid.setErrorStatusCode(200); // invalid — must be >= 400

        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(invalid));

        assertThrows(IllegalStateException.class, () -> new YamlPolicyStore(props));
    }

    @Test
    void rejectsInvalidPolicyOnReload() {
        RateLimiterProperties props = createProps("valid");
        YamlPolicyStore store = new YamlPolicyStore(props);

        Policy invalid = new Policy();
        invalid.setId("bad");
        invalid.setLimit(10);
        invalid.setWindowSeconds(60);
        invalid.setSubjects(List.of("ip"));
        invalid.setErrorStatusCode(300); // invalid

        props.setPolicies(List.of(invalid));
        assertThrows(IllegalStateException.class, store::reload);
    }

    private RateLimiterProperties createProps(String id) {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setPolicies(List.of(createPolicy(id)));
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
