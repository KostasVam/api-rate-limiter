package com.vamva.ratelimiter.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void allowsRequestsWithinLimit() {
        // Policy: login-per-ip, limit=5
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.postForEntity("/api/auth/login", null, String.class);
            assertEquals(200, response.getStatusCode().value(),
                    "Request " + (i + 1) + " should succeed");

            String remaining = response.getHeaders().getFirst("X-RateLimit-Remaining");
            assertNotNull(remaining);
            assertEquals(String.valueOf(4 - i), remaining);
        }
    }

    @Test
    void rejectsWhenLimitExceeded() {
        // Use a unique IP to isolate from other tests
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.99.99.1");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/auth/login", HttpMethod.POST,
                    new HttpEntity<>(headers), String.class);
            assertEquals(200, response.getStatusCode().value());
        }

        // 6th request should be rejected
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("login-per-ip", response.getHeaders().getFirst("X-RateLimit-Policy"));
        assertNotNull(response.getHeaders().getFirst("Retry-After"));
        assertTrue(response.getBody().contains("rate_limit_exceeded"));
    }

    @Test
    void differentSubjectsAreIsolated() {
        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-Forwarded-For", "10.0.0.1");

        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Forwarded-For", "10.0.0.2");

        // Exhaust limit for user A
        for (int i = 0; i < 5; i++) {
            restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                    new HttpEntity<>(headersA), String.class);
        }

        // User A should be blocked
        ResponseEntity<String> blockedA = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headersA), String.class);
        assertEquals(429, blockedA.getStatusCode().value());

        // User B should still be allowed
        ResponseEntity<String> allowedB = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headersB), String.class);
        assertEquals(200, allowedB.getStatusCode().value());
    }
}
