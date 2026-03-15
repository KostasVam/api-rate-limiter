package com.vamva.ratelimiter.integration;

import com.vamva.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @PostMapping("/api/auth/login")
    public Map<String, String> login() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/payments")
    public Map<String, String> payment() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/sliding")
    public Map<String, String> sliding() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/bucket")
    public Map<String, String> bucket() {
        return Map.of("status", "ok");
    }

    @PostMapping("/api/annotated")
    @RateLimit(id = "annotated-per-ip", limit = 3, windowSeconds = 60, subjects = {"ip"})
    public Map<String, String> annotated() {
        return Map.of("status", "ok");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}
