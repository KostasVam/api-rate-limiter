package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.model.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that manages runtime policy updates without application restart.
 *
 * <p>Maintains a thread-safe snapshot of active policies. The {@link PolicyReloadEndpoint}
 * triggers reloads, and the {@link com.vamva.ratelimiter.policy.PolicyResolver} reads
 * the current policies from this service.</p>
 *
 * <p>On startup, policies are loaded from YAML configuration. At runtime, policies
 * can be replaced via the reload endpoint or programmatically via {@link #updatePolicies}.</p>
 */
@Slf4j
@Service
public class PolicyReloadService {

    private final AtomicReference<List<Policy>> activePolicies;
    private final RateLimiterProperties properties;

    public PolicyReloadService(RateLimiterProperties properties) {
        this.properties = properties;
        this.activePolicies = new AtomicReference<>(
                Collections.unmodifiableList(properties.getPolicies()));
        log.info("Loaded {} rate limit policies from configuration", properties.getPolicies().size());
    }

    /**
     * Returns the current active policies. Thread-safe.
     */
    public List<Policy> getActivePolicies() {
        return activePolicies.get();
    }

    /**
     * Replaces all active policies atomically.
     *
     * @param newPolicies the new policy list
     */
    public void updatePolicies(List<Policy> newPolicies) {
        List<Policy> previous = activePolicies.getAndSet(
                Collections.unmodifiableList(newPolicies));
        log.info("Reloaded rate limit policies: {} → {} policies",
                previous.size(), newPolicies.size());
    }

    /**
     * Reloads policies from the original YAML configuration properties.
     */
    public void reloadFromProperties() {
        updatePolicies(properties.getPolicies());
    }

    /**
     * Returns whether the rate limiter is enabled.
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
