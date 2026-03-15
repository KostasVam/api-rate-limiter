package com.vamva.ratelimiter.demo;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("demo")
public class DemoController {

    @PostMapping("/api/auth/login")
    public Map<String, String> login() {
        return Map.of("status", "login_success");
    }

    @PostMapping("/api/payments")
    public Map<String, String> payment() {
        return Map.of("status", "payment_processed");
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}
