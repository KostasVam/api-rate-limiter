package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Extracts the route identifier as {@code METHOD:path} from the request.
 *
 * <p>Example: a {@code POST} request to {@code /api/payments} produces
 * the subject value {@code POST:/api/payments}.</p>
 */
public class RouteExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "route";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return Optional.of(method + ":" + path);
    }
}
