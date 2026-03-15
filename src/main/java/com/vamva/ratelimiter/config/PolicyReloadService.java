package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.policy.PolicyStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Service that manages runtime policy updates by delegating to a {@link PolicyStore}.
 *
 * <p>Acts as a facade over the policy store, providing a stable API for the
 * {@link PolicyReloadEndpoint} and {@link com.vamva.ratelimiter.policy.PolicyResolver}.</p>
 */
@Slf4j
public class PolicyReloadService {

    private final PolicyStore policyStore;
    private final RateLimiterProperties properties;

    public PolicyReloadService(PolicyStore policyStore, RateLimiterProperties properties) {
        this.policyStore = policyStore;
        this.properties = properties;
    }

    /** Returns the current active policies from the policy store. */
    public List<Policy> getActivePolicies() {
        return policyStore.getPolicies();
    }

    /** Triggers a reload in the underlying policy store. */
    public void reload() {
        policyStore.reload();
    }

    /** Returns whether the rate limiter is enabled. */
    public boolean isEnabled() {
        return properties.isEnabled();
    }
}
