package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.model.Policy;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actuator endpoint for viewing and reloading rate limit policies at runtime.
 *
 * <ul>
 *   <li>{@code GET /actuator/ratelimiter} — view current active policies</li>
 *   <li>{@code POST /actuator/ratelimiter} — reload policies from configuration</li>
 * </ul>
 */
@Component
@Endpoint(id = "ratelimiter")
public class PolicyReloadEndpoint {

    private final PolicyReloadService reloadService;

    public PolicyReloadEndpoint(PolicyReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @ReadOperation
    public Map<String, Object> status() {
        List<Policy> policies = reloadService.getActivePolicies();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", reloadService.isEnabled());
        result.put("policyCount", policies.size());
        result.put("policies", policies.stream().map(this::policyInfo).toList());
        return result;
    }

    @WriteOperation
    public Map<String, Object> reload() {
        int before = reloadService.getActivePolicies().size();
        reloadService.reloadFromProperties();
        int after = reloadService.getActivePolicies().size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "reloaded");
        result.put("previousCount", before);
        result.put("currentCount", after);
        return result;
    }

    private Map<String, Object> policyInfo(Policy policy) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", policy.getId());
        info.put("enabled", policy.isEnabled());
        info.put("mode", policy.getMode());
        info.put("algorithm", policy.getAlgorithm());
        info.put("limit", policy.getLimit());
        info.put("windowSeconds", policy.getWindowSeconds());
        info.put("subjects", policy.getSubjects());
        return info;
    }
}
