package com.vamva.ratelimiter.policy;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Immutable value object representing a canonicalized route.
 *
 * <p>Ensures a single, consistent route representation across the entire pipeline:
 * policy matching, subject key generation, structured logging, and metric labels.
 * Eliminates the class of bugs where the same URL produces different strings
 * in different components.</p>
 *
 * <p>Normalization rules (applied once at construction):</p>
 * <ul>
 *   <li>Strip trailing slashes (except root "/")</li>
 *   <li>Strip query string parameters</li>
 *   <li>Trim whitespace</li>
 * </ul>
 *
 * @param method         HTTP method (e.g., "POST")
 * @param normalizedPath normalized URI path (e.g., "/api/payments")
 */
public record CanonicalRoute(String method, String normalizedPath) {

    /**
     * Creates a CanonicalRoute from an HTTP request, normalizing the path.
     *
     * @param request the incoming HTTP request
     * @return a new CanonicalRoute with normalized method and path
     */
    public static CanonicalRoute from(HttpServletRequest request) {
        return new CanonicalRoute(request.getMethod(), normalizePath(request.getRequestURI()));
    }

    /**
     * Returns the route in subject key format: {@code METHOD:/path}
     * <p>Used for composite rate limit keys (e.g., {@code POST:/api/payments}).</p>
     *
     * @return the route as a subject key string
     */
    public String toSubjectKey() {
        return method + ":" + normalizedPath;
    }

    /**
     * Returns the route in log format: {@code METHOD /path}
     * <p>Used for structured logging and debug output.</p>
     *
     * @return the route as a human-readable log string
     */
    public String toLogString() {
        return method + " " + normalizedPath;
    }

    @Override
    public String toString() {
        return toLogString();
    }

    /**
     * Normalizes a raw request URI path.
     *
     * @param path the raw URI path
     * @return the normalized path
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.trim();
    }
}
