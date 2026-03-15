package com.vamva.ratelimiter.subject;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Extracts the client IP address from the request.
 *
 * <p>Checks the {@code X-Forwarded-For} header first (using the leftmost entry),
 * falling back to {@link HttpServletRequest#getRemoteAddr()}.</p>
 *
 * <p><strong>Note:</strong> {@code X-Forwarded-For} is client-provided and can be spoofed.
 * In production, configure trusted proxy IPs at the load balancer level.</p>
 */
@Slf4j
public class IpExtractor implements SubjectExtractor {

    @Override
    public String type() {
        return "ip";
    }

    @Override
    public Optional<String> extract(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String ip = forwarded.split(",")[0].trim();
            if (!ip.isEmpty()) {
                return Optional.of(ip);
            }
            log.warn("X-Forwarded-For header present but empty after parsing: {}", forwarded);
        }
        return Optional.ofNullable(request.getRemoteAddr());
    }
}
