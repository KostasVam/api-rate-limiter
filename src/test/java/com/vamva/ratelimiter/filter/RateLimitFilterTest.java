package com.vamva.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.config.RateLimiterProperties;
import com.vamva.ratelimiter.engine.RateLimitEngine;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.model.RateLimitResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitEngine engine;
    private RateLimitMetrics metrics;
    private RateLimitFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        engine = mock(RateLimitEngine.class);
        metrics = mock(RateLimitMetrics.class);
        filterChain = mock(FilterChain.class);

        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setExcludePaths(List.of("/actuator/**", "/health"));

        filter = new RateLimitFilter(engine, metrics, new ObjectMapper(), properties);
    }

    @Test
    void passesThrough_whenNoPoliciesMatch() throws Exception {
        when(engine.evaluate(any())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    void setsHeaders_whenAllowed() throws Exception {
        RateLimitResult result = RateLimitResult.allowed(100, 95, 1700000000L, "test");
        when(engine.evaluate(any())).thenReturn(Optional.of(result));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals("100", response.getHeader("X-RateLimit-Limit"));
        assertEquals("95", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("1700000000", response.getHeader("X-RateLimit-Reset"));
    }

    @Test
    void returns429_whenRejected() throws Exception {
        RateLimitResult result = RateLimitResult.rejected(100, 1700000000L, "login-per-ip", 42);
        when(engine.evaluate(any())).thenReturn(Optional.of(result));

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertEquals(429, response.getStatus());
        assertEquals("42", response.getHeader("Retry-After"));
        assertEquals("login-per-ip", response.getHeader("X-RateLimit-Policy"));
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("rate_limit_exceeded"));
    }

    @Test
    void recordsMetrics() throws Exception {
        RateLimitResult result = RateLimitResult.allowed(10, 9, 1000L, "test");
        when(engine.evaluate(any())).thenReturn(Optional.of(result));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(metrics).recordRequest(eq("test"), eq("GET /api/test"), eq(true));
        verify(metrics).recordEvaluationTime(anyLong());
    }

    @Test
    void bypassesExcludedPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(engine, never()).evaluate(any());
    }

    @Test
    void bypassesExactExcludedPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(engine, never()).evaluate(any());
    }
}
