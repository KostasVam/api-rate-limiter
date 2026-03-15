package com.vamva.ratelimiter.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.filter.RateLimitFilter;
import com.vamva.ratelimiter.model.Algorithm;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.PolicyMode;
import com.vamva.ratelimiter.model.RateLimitResult;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring MVC interceptor that evaluates {@link RateLimit} annotations on controller methods.
 *
 * <p>Runs after the filter chain but before the controller method executes.
 * If the annotated rate limit is exceeded, sets a request attribute that the
 * {@link RateLimitFilter} can detect, or throws a rate limit exception.</p>
 *
 * <p>Annotation-based policies coexist with YAML policies. Both are evaluated
 * independently — if either rejects, the request is rejected.</p>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterProperties properties;
    private final CompositeKeyBuilder keyBuilder;
    private final RateLimitBackend backend;
    private final ObjectMapper objectMapper;
    private final Map<String, Policy> policyCache = new ConcurrentHashMap<>();

    public RateLimitInterceptor(RateLimiterProperties properties,
                                CompositeKeyBuilder keyBuilder,
                                RateLimitBackend backend,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.keyBuilder = keyBuilder;
        this.backend = backend;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                             Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        Policy policy = toPolicy(annotation);
        Optional<String> subjectKey = keyBuilder.buildKey(policy, request);
        if (subjectKey.isEmpty()) {
            log.debug("Skipping @RateLimit '{}' — subject could not be extracted", annotation.id());
            return true;
        }

        RateLimitResult result = evaluate(policy, subjectKey.get());

        if (!result.isAllowed()) {
            String route = request.getMethod() + " " + request.getRequestURI();
            log.info("policy_id={} mode=annotation subject={} route={} decision=REJECT remaining=0 retry_after={}",
                    annotation.id(), subjectKey.get(), route, result.getRetryAfterSeconds());

            response.setStatus(result.getErrorStatusCode());
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setHeader("X-RateLimit-Policy", annotation.id());
            response.setContentType("application/json");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "rate_limit_exceeded");
            body.put("message", result.getErrorMessage());
            body.put("limit", result.getLimit());
            body.put("remaining", 0);
            body.put("retry_after_seconds", result.getRetryAfterSeconds());
            body.put("policy", annotation.id());
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }

        // Store for header advice
        request.setAttribute(RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE, result);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.getResetEpochSeconds()));

        return true;
    }

    private RateLimitResult evaluate(Policy policy, String subjectKey) {
        long now = Instant.now().getEpochSecond();
        String hashTag = String.format("{%s:%s}", policy.getId(), subjectKey);

        if (policy.getAlgorithm() == Algorithm.TOKEN_BUCKET) {
            String bucketKey = String.format("rl:tb:%s", hashTag);
            int capacity = policy.getEffectiveBurstCapacity();
            double refillRate = (double) policy.getLimit() / policy.getWindowSeconds();
            return backend.tokenBucketConsume(bucketKey, capacity, refillRate, policy.getId());
        }

        long windowStart = now / policy.getWindowSeconds();

        if (policy.getAlgorithm() == Algorithm.SLIDING_WINDOW) {
            String currentKey = String.format("rl:%s:%d", hashTag, windowStart);
            String previousKey = String.format("rl:%s:%d", hashTag, windowStart - 1);
            long windowStartEpoch = windowStart * policy.getWindowSeconds();
            double overlapWeight = 1.0 - ((double) (now - windowStartEpoch) / policy.getWindowSeconds());
            return backend.slidingWindowIncrement(currentKey, previousKey,
                    policy.getLimit(), policy.getWindowSeconds(), overlapWeight, policy.getId());
        }

        String redisKey = String.format("rl:%s:%d", hashTag, windowStart);
        return backend.increment(redisKey, policy.getLimit(), policy.getWindowSeconds(), policy.getId());
    }

    private Policy toPolicy(RateLimit annotation) {
        return policyCache.computeIfAbsent(annotation.id(), id -> {
            Policy policy = new Policy();
            policy.setId(annotation.id());
            policy.setEnabled(true);
            policy.setMode(PolicyMode.ENFORCE);
            policy.setAlgorithm(annotation.algorithm());
            policy.setLimit(annotation.limit());
            policy.setWindowSeconds(annotation.windowSeconds());
            policy.setSubjects(List.of(annotation.subjects()));
            policy.setBurstCapacity(annotation.burstCapacity());
            if (!annotation.errorMessage().isEmpty()) {
                policy.setErrorMessage(annotation.errorMessage());
            }
            if (annotation.errorStatusCode() > 0) {
                policy.setErrorStatusCode(annotation.errorStatusCode());
            }
            return policy;
        });
    }
}
