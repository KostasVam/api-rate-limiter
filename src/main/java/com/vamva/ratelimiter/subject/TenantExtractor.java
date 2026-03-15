package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Extracts the tenant identifier from the {@code X-Tenant-Id} request header.
 */
public class TenantExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "tenant";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        String tenant = request.getHeader("X-Tenant-Id");
        if (tenant != null && !tenant.isBlank()) {
            return Optional.of(tenant);
        }
        return Optional.empty();
    }
}
