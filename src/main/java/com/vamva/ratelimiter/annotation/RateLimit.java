package com.vamva.ratelimiter.annotation;

import com.vamva.ratelimiter.model.Algorithm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a rate limit policy directly on a controller method.
 *
 * <p>This is an alternative to YAML-based policy configuration. Annotation-based
 * policies coexist with YAML policies — both are evaluated. If a method has both
 * a YAML policy and an annotation, both apply (most restrictive wins).</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * &#64;PostMapping("/api/payments")
 * &#64;RateLimit(id = "payments-per-user", limit = 10, windowSeconds = 60, subjects = {"user", "route"})
 * public ResponseEntity&lt;?&gt; createPayment() { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Unique policy identifier. Used in Redis keys, metrics, and logs. */
    String id();

    /** Maximum number of requests allowed within the time window. */
    int limit();

    /** Duration of the rate limit window in seconds. */
    int windowSeconds();

    /** Subject scopes for key generation (e.g., "ip", "user", "api_key"). */
    String[] subjects() default {"ip"};

    /** Rate limiting algorithm. */
    Algorithm algorithm() default Algorithm.FIXED_WINDOW;

    /** Burst capacity for token bucket algorithm. 0 means use limit as capacity. */
    int burstCapacity() default 0;

    /** Custom error message for rejection response. */
    String errorMessage() default "";
}
