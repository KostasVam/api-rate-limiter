package com.vamva.ratelimiter.integration;

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
}
