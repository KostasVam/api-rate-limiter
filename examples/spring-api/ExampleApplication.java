package com.example.myapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Example Spring Boot application demonstrating rate limiter integration.
 *
 * <h2>Setup</h2>
 * <ol>
 *   <li>Add the rate limiter dependency to your build.gradle.kts</li>
 *   <li>Configure policies in application.yml (see below)</li>
 *   <li>The rate limiter filter activates automatically via Spring auto-configuration</li>
 * </ol>
 *
 * <h2>No code changes required</h2>
 * <p>The rate limiter works as middleware — your controllers don't need any modifications.
 * Rate limit headers are added automatically, and 429 responses are returned when limits
 * are exceeded.</p>
 */
@SpringBootApplication
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}

/**
 * Example controller — no rate limiting code needed.
 * Policies are defined in YAML configuration.
 */
@RestController
@RequestMapping("/api")
class PaymentController {

    @PostMapping("/payments")
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> payload) {
        return Map.of(
                "status", "processed",
                "amount", payload.getOrDefault("amount", 0)
        );
    }

    @GetMapping("/payments/{id}")
    public Map<String, Object> getPayment(@PathVariable String id) {
        return Map.of("id", id, "status", "completed");
    }
}

@RestController
class AuthController {

    @PostMapping("/api/auth/login")
    public Map<String, String> login() {
        return Map.of("token", "eyJhbGciOiJIUzI1NiIs...");
    }

    @PostMapping("/api/auth/register")
    public Map<String, String> register() {
        return Map.of("status", "created");
    }
}
