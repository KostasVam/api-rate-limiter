package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Strategy interface for extracting a rate limit subject from an HTTP request.
 *
 * <p>Each implementation identifies a specific subject type (e.g., client IP, user ID)
 * and extracts the corresponding value from the request. Extractors are pluggable
 * and registered as Spring beans.</p>
 *
 * @see CompositeKeyBuilder
 */
public interface SubjectExtractor {

    /**
     * Returns the subject type identifier (e.g., "ip", "user", "api_key", "tenant", "route").
     *
     * @return the subject type string, used as prefix in composite keys
     */
    String type();

    /**
     * Extracts the subject value from the request.
     *
     * @param request the incoming HTTP request
     * @return the extracted subject value, or empty if the subject cannot be determined
     */
    Optional<String> extract(HttpServletRequest request);
}
