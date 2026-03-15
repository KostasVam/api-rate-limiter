package com.vamva.ratelimiter.subject;

import com.vamva.ratelimiter.model.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CompositeKeyBuilderTest {

    private final CompositeKeyBuilder builder = new CompositeKeyBuilder(List.of(
            new IpExtractor(),
            new RouteExtractor(),
            new ApiKeyExtractor()
    ));

    @Test
    void singleSubjectKey() {
        Policy policy = createPolicy(List.of("ip"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");

        Optional<String> key = builder.buildKey(policy, request);

        assertTrue(key.isPresent());
        assertEquals("ip:192.168.1.1", key.get());
    }

    @Test
    void compositeKey() {
        Policy policy = createPolicy(List.of("ip", "route"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments");
        request.setRemoteAddr("10.0.0.1");

        Optional<String> key = builder.buildKey(policy, request);

        assertTrue(key.isPresent());
        assertEquals("ip:10.0.0.1|route:POST:/api/payments", key.get());
    }

    @Test
    void missingExtractorReturnsEmpty() {
        Policy policy = createPolicy(List.of("tenant"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        Optional<String> key = builder.buildKey(policy, request);

        assertTrue(key.isEmpty());
    }

    @Test
    void missingValueReturnsEmpty() {
        Policy policy = createPolicy(List.of("api_key"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        Optional<String> key = builder.buildKey(policy, request);

        assertTrue(key.isEmpty());
    }

    private Policy createPolicy(List<String> subjects) {
        Policy policy = new Policy();
        policy.setId("test");
        policy.setSubjects(subjects);
        return policy;
    }
}
