package com.vamva.ratelimiter.policy;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Static utility facade over {@link CanonicalRoute} for convenience.
 *
 * <p>Prefer using {@link CanonicalRoute#from(HttpServletRequest)} directly
 * when you need to pass the route through multiple components. Use this
 * utility for one-off normalization calls.</p>
 */
public final class RouteNormalizer {

    private RouteNormalizer() {}

    /**
     * Normalizes a raw URI path (strips trailing slashes, query strings).
     *
     * @param path the raw URI path to normalize
     * @return the normalized path
     */
    public static String normalizePath(String path) {
        return CanonicalRoute.normalizePath(path);
    }

    /**
     * Builds canonical route from request.
     *
     * @param request the incoming HTTP request
     * @return the canonical route derived from the request
     */
    public static CanonicalRoute canonicalRoute(HttpServletRequest request) {
        return CanonicalRoute.from(request);
    }

    /**
     * Human-readable route for logging.
     *
     * @param request the incoming HTTP request
     * @return the route formatted for log output
     */
    public static String logRoute(HttpServletRequest request) {
        return CanonicalRoute.from(request).toLogString();
    }
}
