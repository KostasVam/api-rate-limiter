package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Extracts the authenticated user identity from the request.
 *
 * <p>Checks {@link HttpServletRequest#getUserPrincipal()} first (for Spring Security
 * or container-managed auth), then falls back to the {@code X-User-Id} header
 * for pre-authenticated scenarios (e.g., API gateway).</p>
 */
@Component
public class UserExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "user";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return Optional.of(principal.getName());
        }

        String userHeader = request.getHeader("X-User-Id");
        if (userHeader != null && !userHeader.isBlank()) {
            return Optional.of(userHeader);
        }

        return Optional.empty();
    }
}
