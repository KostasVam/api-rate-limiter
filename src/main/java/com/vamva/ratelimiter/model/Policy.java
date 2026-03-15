package com.vamva.ratelimiter.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Represents a rate limiting policy that defines when and how to limit requests.
 *
 * <p>Each policy specifies match conditions (paths, methods), subject scopes
 * for key generation, and rate limit parameters (requests per window).</p>
 *
 * <p>Policies support two modes:</p>
 * <ul>
 *   <li>{@code enforce} (default) — requests exceeding the limit are rejected with 429</li>
 *   <li>{@code observe} — full evaluation occurs (counters incremented, metrics/logs recorded)
 *       but requests are never rejected. Use this for safe rollout of new policies.</li>
 * </ul>
 */
@Data
public class Policy {

    /** Unique identifier for this policy, used in Redis keys and metrics. */
    @NotBlank
    private String id;

    /** Whether this policy is active. Disabled policies are skipped during evaluation. */
    private boolean enabled = true;

    /**
     * Policy enforcement mode.
     * <ul>
     *   <li>{@code enforce} — reject requests that exceed the limit (default)</li>
     *   <li>{@code observe} — evaluate and log/meter, but never reject (shadow mode)</li>
     * </ul>
     */
    private PolicyMode mode = PolicyMode.ENFORCE;

    /** Priority for evaluation ordering. Lower values are evaluated first. */
    @Min(0)
    private int priority;

    /** Conditions that determine whether this policy applies to a given request. */
    @Valid
    private MatchCondition match = new MatchCondition();

    /** Subject scopes used to build the rate limit key (e.g., "ip", "user", "route"). */
    @NotEmpty
    private List<String> subjects = List.of();

    /** Maximum number of requests allowed within the time window. */
    @Min(1)
    private int limit;

    /** Duration of the rate limit window in seconds. */
    @Min(1)
    private int windowSeconds;

    /** Rate limiting algorithm. Defaults to fixed window. */
    private Algorithm algorithm = Algorithm.FIXED_WINDOW;

    /**
     * Maximum burst capacity for token bucket algorithm.
     * Defaults to the same value as {@code limit} if not specified.
     * Only used when {@code algorithm = TOKEN_BUCKET}.
     */
    @Min(0)
    private int burstCapacity;

    /** Custom error message returned in the 429 response body. Defaults to "Too many requests". */
    private String errorMessage;

    /**
     * Custom HTTP status code for rejection. Defaults to 429 if not set (0).
     * When specified, must be >= 400.
     */
    private int errorStatusCode;

    /**
     * Returns the effective burst capacity — defaults to {@code limit} if not explicitly set.
     */
    public int getEffectiveBurstCapacity() {
        return burstCapacity > 0 ? burstCapacity : limit;
    }

    /** Returns the error message, defaulting to "Too many requests". */
    public String getEffectiveErrorMessage() {
        return errorMessage != null && !errorMessage.isBlank() ? errorMessage : "Too many requests";
    }

    /** Returns the error status code, defaulting to 429. */
    public int getEffectiveErrorStatusCode() {
        return errorStatusCode > 0 ? errorStatusCode : 429;
    }
}
