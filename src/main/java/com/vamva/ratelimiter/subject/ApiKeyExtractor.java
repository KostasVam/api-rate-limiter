package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Extracts the API key from the {@code X-API-Key} request header.
 */
@Component
public class ApiKeyExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "api_key";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return Optional.of(apiKey);
        }
        return Optional.empty();
    }
}
