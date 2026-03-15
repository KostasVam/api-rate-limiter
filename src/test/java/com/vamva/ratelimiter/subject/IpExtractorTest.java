package com.vamva.ratelimiter.subject;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IpExtractorTest {

    private final IpExtractor extractor = new IpExtractor();

    @Test
    void type() {
        assertEquals("ip", extractor.type());
    }

    @Test
    void extractFromRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        Optional<String> result = extractor.extract(request);

        assertTrue(result.isPresent());
        assertEquals("192.168.1.100", result.get());
    }

    @Test
    void extractFromXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 70.41.3.18");

        Optional<String> result = extractor.extract(request);

        assertTrue(result.isPresent());
        assertEquals("203.0.113.10", result.get());
    }

    @Test
    void xForwardedForTakesPrecedence() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        Optional<String> result = extractor.extract(request);

        assertTrue(result.isPresent());
        assertEquals("8.8.8.8", result.get());
    }
}
