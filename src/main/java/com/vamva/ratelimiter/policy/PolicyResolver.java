package com.vamva.ratelimiter.policy;

import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.model.Policy;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves which {@link Policy} instances apply to an incoming HTTP request.
 *
 * <p>Reads active policies from {@link PolicyReloadService}, which supports
 * runtime reloading. Filters by enabled status and match conditions (path + method),
 * then returns them sorted by priority (lower value = higher priority).
 * Path matching uses Spring's {@link org.springframework.util.AntPathMatcher}
 * for glob patterns like {@code /api/**}.</p>
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
        String path = normalizePath(request.getRequestURI());
        String method = request.getMethod();

        return reloadService.getActivePolicies().stream()
                .filter(Policy::isEnabled)
                .filter(p -> p.getMatch().matches(path, method))
                .sorted(Comparator.comparingInt(Policy::getPriority))
                .toList();
    }

    /**
     * Normalizes the request path by stripping trailing slashes.
     */
    private String normalizePath(String path) {
        if (path != null && path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
