package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.model.Policy;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves which {@link Policy} instances apply to an incoming HTTP request.
 *
 * <p>Uses {@link CanonicalRoute} for path normalization, ensuring consistency
 * with subject key generation and logging throughout the pipeline.</p>
 */
public class PolicyResolver {

    private final PolicyReloadService reloadService;

    public PolicyResolver(PolicyReloadService reloadService) {
        this.reloadService = reloadService;
    }

    /**
     * Finds all enabled policies whose match conditions apply to the given request.
     *
     * @param request the incoming HTTP request
     * @return matching policies sorted by priority (ascending), or an empty list
     */
    public List<Policy> resolve(HttpServletRequest request) {
        CanonicalRoute route = CanonicalRoute.from(request);

        return reloadService.getActivePolicies().stream()
                .filter(Policy::isEnabled)
                .filter(p -> p.getMatch().matches(route.normalizedPath(), route.method()))
                .sorted(Comparator.comparingInt(Policy::getPriority))
                .toList();
    }
}
