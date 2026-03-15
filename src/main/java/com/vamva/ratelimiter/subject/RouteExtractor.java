package com.vamva.ratelimiter.subject;

import com.vamva.ratelimiter.policy.CanonicalRoute;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Extracts the route identifier from the request using {@link CanonicalRoute}.
 *
 * <p>Produces a canonical subject key (e.g., {@code POST:/api/payments}) that is
 * guaranteed to be consistent with the path normalization used in policy matching.</p>
 */
public class RouteExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "route";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        return Optional.of(CanonicalRoute.from(request).toSubjectKey());
    }
}
