package com.vamva.ratelimiter.subject;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserExtractorTest {

    private final UserExtractor extractor = new UserExtractor();

    @Test
    void type() {
        assertEquals("user", extractor.type());
    }

    @Test
    void extractFromPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "user-42");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isPresent());
        assertEquals("user-42", result.get());
    }

    @Test
    void extractFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-99");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isPresent());
        assertEquals("user-99", result.get());
    }

    @Test
    void principalTakesPrecedenceOverHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "principal-user");
        request.addHeader("X-User-Id", "header-user");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isPresent());
        assertEquals("principal-user", result.get());
    }

    @Test
    void returnsEmptyWhenNoUserInfo() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Optional<String> result = extractor.extract(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresBlankHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "   ");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresBlankPrincipalName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "  ");
        request.addHeader("X-User-Id", "fallback-user");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isPresent());
        assertEquals("fallback-user", result.get());
    }
}
