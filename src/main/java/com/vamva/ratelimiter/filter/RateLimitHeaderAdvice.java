package com.vamva.ratelimiter.filter;

import com.vamva.ratelimiter.model.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Ensures rate limit headers are present on all responses, including error responses.
 *
 * <p>When a controller throws an exception, Spring's error handling may create a new
 * response that doesn't carry the headers set by {@link RateLimitFilter}. This advice
 * re-applies them from the request attribute stored by the filter.</p>
 *
 * <p>Note: {@code @ControllerAdvice} is required for Spring MVC to recognize this as
 * a {@link ResponseBodyAdvice}. The bean is still created via auto-configuration,
 * not component scanning.</p>
 */
@ControllerAdvice
public class RateLimitHeaderAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            RateLimitResult result = (RateLimitResult) httpRequest.getAttribute(
                    RateLimitFilter.RATE_LIMIT_RESULT_ATTRIBUTE);

            if (result != null) {
                response.getHeaders().putIfAbsent("X-RateLimit-Limit",
                        java.util.List.of(String.valueOf(result.getLimit())));
                response.getHeaders().putIfAbsent("X-RateLimit-Remaining",
                        java.util.List.of(String.valueOf(result.getRemaining())));
                response.getHeaders().putIfAbsent("X-RateLimit-Reset",
                        java.util.List.of(String.valueOf(result.getResetEpochSeconds())));
                if (!result.isAllowed()) {
                    response.getHeaders().putIfAbsent("Retry-After",
                            java.util.List.of(String.valueOf(result.getRetryAfterSeconds())));
                    response.getHeaders().putIfAbsent("X-RateLimit-Policy",
                            java.util.List.of(result.getPolicyId()));
                }
            }
        }
        return body;
    }
}
