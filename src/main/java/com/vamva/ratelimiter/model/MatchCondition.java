package com.vamva.ratelimiter.model;

import lombok.Data;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Defines the conditions under which a {@link Policy} applies to an incoming request.
 *
 * <p>Supports Ant-style path patterns (e.g., {@code /api/payments/**}) and
 * HTTP method filtering. Empty lists match all paths/methods respectively.</p>
 */
@Data
public class MatchCondition {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Ant-style path patterns to match against (e.g., "/api/auth/login", "/api/**"). */
    private List<String> paths = List.of();

    /** HTTP methods to match against (e.g., "POST", "GET"). Empty matches all methods. */
    private List<String> methods = List.of();

    /**
     * Checks whether the given request path and method match this condition.
     *
     * @param requestPath   the request URI path
     * @param requestMethod the HTTP method (GET, POST, etc.)
     * @return {@code true} if both path and method match (or their lists are empty)
     */
    public boolean matches(String requestPath, String requestMethod) {
        boolean methodMatch = methods.isEmpty()
                || methods.stream().anyMatch(m -> m.equalsIgnoreCase(requestMethod));

        boolean pathMatch = paths.isEmpty()
                || paths.stream().anyMatch(p -> PATH_MATCHER.match(p, requestPath));

        return methodMatch && pathMatch;
    }
}
