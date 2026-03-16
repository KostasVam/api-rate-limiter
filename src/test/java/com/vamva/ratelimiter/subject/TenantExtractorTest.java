package com.vamva.ratelimiter.subject;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantExtractorTest {

    private final TenantExtractor extractor = new TenantExtractor();

    @Test
    void type() {
        assertEquals("tenant", extractor.type());
    }

    @Test
    void extractFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "acme-corp");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isPresent());
        assertEquals("acme-corp", result.get());
    }

    @Test
    void returnsEmptyWhenMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Optional<String> result = extractor.extract(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void ignoresBlankHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "  ");

        Optional<String> result = extractor.extract(request);
        assertTrue(result.isEmpty());
    }
}
