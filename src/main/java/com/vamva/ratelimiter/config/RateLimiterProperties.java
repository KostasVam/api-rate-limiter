package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.model.Policy;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the rate limiter, bound from {@code rate-limiter.*} in YAML.
 *
 * <p>Defines the backend type (redis/in-memory), failure mode, and the list of
 * rate limiting policies to enforce.</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** Whether the rate limiter is enabled globally. */
    private boolean enabled = true;

    /** If {@code true}, requests are allowed when the backend (Redis) is unavailable. */
    private boolean failOpen = true;

    /** Storage backend type: "redis" or "in-memory". */
    private String backend = "redis";

    /** List of rate limiting policies to enforce. */
    @Valid
    private List<Policy> policies = new ArrayList<>();
}
