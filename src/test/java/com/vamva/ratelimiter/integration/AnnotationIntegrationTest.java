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

/**
 * Integration tests for annotation-based rate limiting via {@code @RateLimit}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AnnotationIntegrationTest {

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
    void annotationRateLimitAllows() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.60.0.1");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/annotated", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("3", response.getHeaders().getFirst("X-RateLimit-Limit"));
    }

    @Test
    void annotationRateLimitRejects() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.60.0.2");

        // Exhaust the 3-request limit
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/annotated", HttpMethod.POST,
                    new HttpEntity<>(headers), String.class);
            assertEquals(200, response.getStatusCode().value(),
                    "Request " + (i + 1) + " should succeed");
        }

        // 4th should be rejected
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/annotated", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("annotated-per-ip", response.getHeaders().getFirst("X-RateLimit-Policy"));
        assertTrue(response.getBody().contains("rate_limit_exceeded"));
    }

    @Test
    void annotationDifferentSubjectsAreIsolated() {
        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-Forwarded-For", "10.60.1.1");
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Forwarded-For", "10.60.1.2");

        // Exhaust A
        for (int i = 0; i < 3; i++) {
            restTemplate.exchange("/api/annotated", HttpMethod.POST,
                    new HttpEntity<>(headersA), String.class);
        }

        // A blocked
        ResponseEntity<String> blockedA = restTemplate.exchange(
                "/api/annotated", HttpMethod.POST,
                new HttpEntity<>(headersA), String.class);
        assertEquals(429, blockedA.getStatusCode().value());

        // B still allowed
        ResponseEntity<String> allowedB = restTemplate.exchange(
                "/api/annotated", HttpMethod.POST,
                new HttpEntity<>(headersB), String.class);
        assertEquals(200, allowedB.getStatusCode().value());
    }
}
