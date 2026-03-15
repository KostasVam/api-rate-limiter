package com.vamva.ratelimiter.subject;

import com.vamva.ratelimiter.policy.RouteNormalizer;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Extracts the route identifier from the request using {@link RouteNormalizer}.
 *
 * <p>Produces a canonical route string (e.g., {@code POST:/api/payments}) that is
 * consistent with the path normalization used in policy matching. This ensures
 * that two URLs considered "the same" for matching also produce the same subject key.</p>
 */
public class RouteExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "route";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        return Optional.of(RouteNormalizer.canonicalRoute(request));
    }
}
