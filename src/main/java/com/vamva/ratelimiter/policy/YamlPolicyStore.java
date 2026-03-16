package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.model.Policy;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link PolicyStore} implementation that reads policies from Spring
 * YAML/properties configuration.
 *
 * <p>Policies are loaded at startup from {@link RateLimiterProperties} and
 * cached in an {@link AtomicReference} for thread-safe access. Calling
 * {@link #reload()} re-reads from properties (useful after Spring Cloud
 * Config refresh or property source update).</p>
 */
@Slf4j
public class YamlPolicyStore implements PolicyStore {

    private final RateLimiterProperties properties;
    private final AtomicReference<List<Policy>> activePolicies;

    /**
     * Creates a YamlPolicyStore and loads initial policies from properties.
     *
     * @param properties the rate limiter configuration properties
     */
    public YamlPolicyStore(RateLimiterProperties properties) {
        this.properties = properties;
        List<Policy> policies = properties.getPolicies();
        validatePolicies(policies);
        this.activePolicies = new AtomicReference<>(Collections.unmodifiableList(policies));
        log.info("Loaded {} rate limit policies from YAML configuration", policies.size());
    }

    @Override
    public List<Policy> getPolicies() {
        return activePolicies.get();
    }

    @Override
    public void reload() {
        List<Policy> newPolicies = properties.getPolicies();
        validatePolicies(newPolicies);
        List<Policy> previous = activePolicies.getAndSet(
                Collections.unmodifiableList(newPolicies));
        log.info("Reloaded YAML policies: {} → {} policies", previous.size(), newPolicies.size());
    }

    private void validatePolicies(List<Policy> policies) {
        for (Policy policy : policies) {
            policy.validate();
        }
    }
}
