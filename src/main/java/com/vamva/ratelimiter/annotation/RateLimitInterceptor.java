package com.vamva.ratelimiter.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.engine.PolicyEvaluator;
import com.vamva.ratelimiter.filter.RateLimitFilter;
import com.vamva.ratelimiter.model.Policy;
import com.vamva.ratelimiter.model.PolicyMode;
import com.vamva.ratelimiter.model.RateLimitResult;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring MVC interceptor that evaluates {@link RateLimit} annotations on controller methods.
 *
 * <p>Uses the shared {@link PolicyEvaluator} for algorithm dispatch and backend calls,
 * ensuring identical behavior between annotation-based and YAML-based policies.</p>
 *
 * <p>Annotation-based policies coexist with YAML policies. Both are evaluated
 * independently — if either rejects, the request is rejected.</p>
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterProperties properties;
    private final CompositeKeyBuilder keyBuilder;
    private final PolicyEvaluator policyEvaluator;
    private final ObjectMapper objectMapper;
    private final Map<String, Policy> policyCache = new ConcurrentHashMap<>();

    public RateLimitInterceptor(RateLimiterProperties properties,
                                CompositeKeyBuilder keyBuilder,
                                PolicyEvaluator policyEvaluator,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.keyBuilder = keyBuilder;
        this.policyEvaluator = policyEvaluator;
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

        // Use shared PolicyEvaluator — same execution path as YAML policies
        RateLimitResult rawResult = policyEvaluator.evaluate(policy, subjectKey.get());

        // Enrich with custom error config
        RateLimitResult result = rawResult;
        if (!rawResult.isAllowed()) {
            result = RateLimitResult.rejected(
                    rawResult.getLimit(), rawResult.getResetEpochSeconds(),
                    rawResult.getPolicyId(), rawResult.getRetryAfterSeconds(),
                    policy.getEffectiveErrorMessage(), policy.getEffectiveErrorStatusCode());
        }

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
