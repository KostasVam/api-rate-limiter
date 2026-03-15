package com.vamva.ratelimiter.policy;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Canonical route normalization used across the entire rate limiter pipeline.
 *
 * <p>Provides a single source of truth for route representation, ensuring
 * consistency between policy matching, subject key generation, logging,
 * and metric labels. Without centralized normalization, the same request
 * could produce different route strings in different components.</p>
 *
 * <p>Normalization rules:</p>
 * <ul>
 *   <li>Strip trailing slashes (except root "/")</li>
 *   <li>Strip query string parameters</li>
 *   <li>Trim whitespace</li>
 * </ul>
 *
 * <p>Used by:</p>
 * <ul>
 *   <li>{@link PolicyResolver} — for policy path matching</li>
 *   <li>{@link com.vamva.ratelimiter.subject.RouteExtractor} — for subject key generation</li>
 *   <li>{@link com.vamva.ratelimiter.engine.RateLimitEngine} — for structured logging</li>
 *   <li>{@link com.vamva.ratelimiter.filter.RateLimitFilter} — for log route context</li>
 * </ul>
 */
public final class RouteNormalizer {

    private RouteNormalizer() {}

    /**
     * Normalizes a raw request URI path.
     *
     * @param path raw URI path (e.g., "/api/payments/", "/api/users?page=1")
     * @return normalized path (e.g., "/api/payments", "/api/users")
     */
    public static String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        // Strip query string
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        // Strip trailing slash (except root)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        return path.trim();
    }

    /**
     * Builds the canonical route string from method and path.
     *
     * @param method HTTP method (e.g., "POST")
     * @param path   raw or normalized URI path
     * @return canonical route (e.g., "POST:/api/payments")
     */
    public static String canonicalRoute(String method, String path) {
        return method + ":" + normalizePath(path);
    }

    /**
     * Builds the canonical route string from an HTTP request.
     *
     * @param request the HTTP request
     * @return canonical route (e.g., "POST:/api/payments")
     */
    public static String canonicalRoute(HttpServletRequest request) {
        return canonicalRoute(request.getMethod(), request.getRequestURI());
    }

    /**
     * Builds a human-readable route for logging (space-separated).
     *
     * @param request the HTTP request
     * @return log route (e.g., "POST /api/payments")
     */
    public static String logRoute(HttpServletRequest request) {
        return request.getMethod() + " " + normalizePath(request.getRequestURI());
    }
}
