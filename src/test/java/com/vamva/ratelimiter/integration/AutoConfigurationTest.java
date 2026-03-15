package com.vamva.ratelimiter.integration;

import com.vamva.ratelimiter.annotation.RateLimitInterceptor;
import com.vamva.ratelimiter.backend.InMemoryBackend;
import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.backend.RedisBackend;
import com.vamva.ratelimiter.config.PolicyReloadEndpoint;
import com.vamva.ratelimiter.config.PolicyReloadService;
import com.vamva.ratelimiter.config.RateLimiterAutoConfiguration;
import com.vamva.ratelimiter.engine.PolicyEvaluator;
import com.vamva.ratelimiter.engine.RateLimitEngine;
import com.vamva.ratelimiter.filter.RateLimitFilter;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.policy.PolicyResolver;
import com.vamva.ratelimiter.subject.CompositeKeyBuilder;
import com.vamva.ratelimiter.subject.IpExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the auto-configuration correctly registers all beans
 * without requiring component scanning of the library package.
 */
class AutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RateLimiterAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    WebMvcAutoConfiguration.class
            ))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                    "rate-limiter.enabled=true",
                    "rate-limiter.backend=in-memory",
                    "rate-limiter.policies[0].id=test-policy",
                    "rate-limiter.policies[0].limit=10",
                    "rate-limiter.policies[0].window-seconds=60",
                    "rate-limiter.policies[0].subjects[0]=ip",
                    "rate-limiter.policies[0].match.paths[0]=/api/**"
            );

    @Test
    void allCoreBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RateLimitBackend.class);
            assertThat(context).hasSingleBean(InMemoryBackend.class);
            assertThat(context).hasSingleBean(IpExtractor.class);
            assertThat(context).hasSingleBean(CompositeKeyBuilder.class);
            assertThat(context).hasSingleBean(RateLimitMetrics.class);
            assertThat(context).hasSingleBean(PolicyReloadService.class);
            assertThat(context).hasSingleBean(PolicyResolver.class);
            assertThat(context).hasSingleBean(PolicyEvaluator.class);
            assertThat(context).hasSingleBean(RateLimitEngine.class);
            assertThat(context).hasSingleBean(RateLimitFilter.class);
        });
    }

    @Test
    void inMemoryBackendSelected() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(InMemoryBackend.class);
            assertThat(context).doesNotHaveBean(RedisBackend.class);
        });
    }

    @Test
    void disabledWhenPropertyFalse() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RateLimiterAutoConfiguration.class,
                        JacksonAutoConfiguration.class
                ))
                .withPropertyValues("rate-limiter.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitEngine.class);
                    assertThat(context).doesNotHaveBean(RateLimitFilter.class);
                });
    }

    @Test
    void policiesLoadedFromProperties() {
        contextRunner.run(context -> {
            PolicyReloadService reloadService = context.getBean(PolicyReloadService.class);
            assertThat(reloadService.getActivePolicies()).hasSize(1);
            assertThat(reloadService.getActivePolicies().get(0).getId()).isEqualTo("test-policy");
        });
    }
}
