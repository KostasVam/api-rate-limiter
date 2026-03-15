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
 * Chaos tests that verify rate limiter behavior during Redis failures.
 * Tests fail-open semantics: when Redis is down, requests should be allowed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ChaosTest {

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
    void failOpenWhenRedisDown() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.50.0.1");

        // Verify rate limiting works normally
        ResponseEntity<String> normalResponse = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, normalResponse.getStatusCode().value());
        assertNotNull(normalResponse.getHeaders().getFirst("X-RateLimit-Remaining"));

        // Stop Redis
        redis.stop();

        // Requests should still succeed (fail-open)
        ResponseEntity<String> failOpenResponse = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, failOpenResponse.getStatusCode().value(),
                "Should allow request when Redis is down (fail-open)");
    }

    @Test
    void recoversWhenRedisComesBack() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "10.50.0.2");

        // Verify working
        ResponseEntity<String> before = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, before.getStatusCode().value());

        // Stop Redis
        redis.stop();

        // Should still work (fail-open)
        ResponseEntity<String> during = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, during.getStatusCode().value());

        // Restart Redis
        redis.start();

        // Should work normally again with rate limiting
        ResponseEntity<String> after = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertEquals(200, after.getStatusCode().value());
    }

    @Test
    void bypassPathsWorkWithoutRedis() {
        // Stop Redis
        redis.stop();

        // Health check should work regardless
        ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);
        assertEquals(200, response.getStatusCode().value());

        // Restart for other tests
        redis.start();
    }
}
