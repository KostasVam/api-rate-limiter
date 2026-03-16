package com.vamva.ratelimiter.policy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalRouteTest {

    @Test
    void toSubjectKey() {
        CanonicalRoute route = CanonicalRoute.from(new MockHttpServletRequest("POST", "/api/payments"));
        assertEquals("POST:/api/payments", route.toSubjectKey());
    }

    @Test
    void toLogString() {
        CanonicalRoute route = CanonicalRoute.from(new MockHttpServletRequest("GET", "/api/users"));
        assertEquals("GET /api/users", route.toLogString());
    }

    @Test
    void toString_sameAsLogString() {
        CanonicalRoute route = CanonicalRoute.from(new MockHttpServletRequest("DELETE", "/api/items"));
        assertEquals(route.toLogString(), route.toString());
    }

    @Test
    void normalizesTrailingSlash() {
        CanonicalRoute route = CanonicalRoute.from(new MockHttpServletRequest("PUT", "/api/data/"));
        assertEquals("/api/data", route.normalizedPath());
    }

    @Test
    void normalizesQueryString() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/search?q=test");
        CanonicalRoute route = CanonicalRoute.from(request);
        assertEquals("/api/search", route.normalizedPath());
    }
}
