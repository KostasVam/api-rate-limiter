package com.vamva.ratelimiter.policy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class RouteNormalizerTest {

    @Test
    void normalizePath_stripsTrailingSlash() {
        assertEquals("/api/payments", RouteNormalizer.normalizePath("/api/payments/"));
    }

    @Test
    void normalizePath_stripsQueryString() {
        assertEquals("/api/users", RouteNormalizer.normalizePath("/api/users?page=1&size=10"));
    }

    @Test
    void normalizePath_preservesRoot() {
        assertEquals("/", RouteNormalizer.normalizePath("/"));
    }

    @Test
    void normalizePath_handlesNull() {
        assertEquals("/", RouteNormalizer.normalizePath(null));
    }

    @Test
    void normalizePath_handlesEmpty() {
        assertEquals("/", RouteNormalizer.normalizePath(""));
    }

    @Test
    void canonicalRoute_fromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/");
        CanonicalRoute route = RouteNormalizer.canonicalRoute(request);
        assertEquals("POST", route.method());
        assertEquals("/api/payments", route.normalizedPath());
    }

    @Test
    void logRoute_format() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users?page=1");
        assertEquals("GET /api/users", RouteNormalizer.logRoute(request));
    }
}
