package com.vamva.ratelimiter.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("algorithms")
class SlidingWindowIntegrationTest {

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
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.20.0.1");

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/sliding", HttpMethod.POST,
                    new HttpEntity<>(headers), String.class);
            assertEquals(200, response.getStatusCode().value(),
                    "Request " + (i + 1) + " should succeed");
            assertNotNull(response.getHeaders().getFirst("X-RateLimit-Remaining"));
        }
    }

    @Test
    void rejectsWhenLimitExceeded() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.20.0.2");

        // Exhaust limit
        for (int i = 0; i < 5; i++) {
            restTemplate.exchange("/api/sliding", HttpMethod.POST,
                    new HttpEntity<>(headers), String.class);
        }

        // 6th should be rejected
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/sliding", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(429, response.getStatusCode().value());
        assertEquals("sliding-per-ip", response.getHeaders().getFirst("X-RateLimit-Policy"));
    }

    @Test
    void differentSubjectsAreIsolated() {
        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-Forwarded-For", "10.20.1.1");
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Forwarded-For", "10.20.1.2");

        // Exhaust A
        for (int i = 0; i < 5; i++) {
            restTemplate.exchange("/api/sliding", HttpMethod.POST,
                    new HttpEntity<>(headersA), String.class);
        }

        ResponseEntity<String> blockedA = restTemplate.exchange(
                "/api/sliding", HttpMethod.POST,
                new HttpEntity<>(headersA), String.class);
        assertEquals(429, blockedA.getStatusCode().value());

        // B should still work
        ResponseEntity<String> allowedB = restTemplate.exchange(
                "/api/sliding", HttpMethod.POST,
                new HttpEntity<>(headersB), String.class);
        assertEquals(200, allowedB.getStatusCode().value());
    }
}
