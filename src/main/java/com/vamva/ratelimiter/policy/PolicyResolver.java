package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.model.Policy;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves which {@link Policy} instances apply to an incoming HTTP request.
 *
 * <p>Uses {@link RouteNormalizer} for canonical path normalization, ensuring
 * consistency with subject key generation and logging.</p>
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
        String path = RouteNormalizer.normalizePath(request.getRequestURI());
        String method = request.getMethod();

        return reloadService.getActivePolicies().stream()
                .filter(Policy::isEnabled)
                .filter(p -> p.getMatch().matches(path, method))
                .sorted(Comparator.comparingInt(Policy::getPriority))
                .toList();
    }
}
