package com.vamva.ratelimiter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vamva.ratelimiter.annotation.RateLimitInterceptor;
import com.vamva.ratelimiter.backend.InMemoryBackend;
import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.backend.RedisBackend;
import com.vamva.ratelimiter.engine.PolicyEvaluator;
import com.vamva.ratelimiter.engine.RateLimitEngine;
import com.vamva.ratelimiter.filter.RateLimitFilter;
import com.vamva.ratelimiter.filter.RateLimitHeaderAdvice;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import com.vamva.ratelimiter.policy.PolicyResolver;
import com.vamva.ratelimiter.policy.PolicyStore;
import com.vamva.ratelimiter.policy.YamlPolicyStore;
import com.vamva.ratelimiter.subject.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring Boot auto-configuration for the rate limiter library.
 *
 * <p>All rate limiter beans are declared here rather than via component scanning,
 * making this a proper Spring Boot starter. Consumer applications only need to
 * add the dependency and configure policies in YAML — no package scanning required.</p>
 *
 * <p>Beans are conditional on {@code rate-limiter.enabled=true} (default).
 * The backend selection is controlled by {@code rate-limiter.backend} (redis/in-memory).</p>
 *
 * <p>All beans use {@link ConditionalOnMissingBean} where appropriate, allowing
 * consumer applications to override any component.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimiterProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    // ── Time ─────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock rateLimiterClock() {
        return Clock.systemUTC();
    }

    // ── Backend ──────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(name = "rate-limiter.backend", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(RateLimitBackend.class)
    public RateLimitBackend redisBackend(StringRedisTemplate redisTemplate,
                                         RateLimiterProperties properties,
                                         RateLimitMetrics metrics) {
        return new RedisBackend(redisTemplate, properties.isFailOpen(), metrics);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limiter.backend", havingValue = "in-memory")
    @ConditionalOnMissingBean(RateLimitBackend.class)
    public RateLimitBackend inMemoryBackend(Clock clock) {
        return new InMemoryBackend(clock);
    }

    // ── Subject Extractors ───────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(IpExtractor.class)
    public IpExtractor ipExtractor() {
        return new IpExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(UserExtractor.class)
    public UserExtractor userExtractor() {
        return new UserExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(ApiKeyExtractor.class)
    public ApiKeyExtractor apiKeyExtractor() {
        return new ApiKeyExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(TenantExtractor.class)
    public TenantExtractor tenantExtractor() {
        return new TenantExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(RouteExtractor.class)
    public RouteExtractor routeExtractor() {
        return new RouteExtractor();
    }

    @Bean
    @ConditionalOnMissingBean(CompositeKeyBuilder.class)
    public CompositeKeyBuilder compositeKeyBuilder(List<SubjectExtractor> extractors) {
        return new CompositeKeyBuilder(extractors);
    }

    // ── Metrics ──────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(RateLimitMetrics.class)
    public RateLimitMetrics rateLimitMetrics(MeterRegistry meterRegistry) {
        return new RateLimitMetrics(meterRegistry);
    }

    // ── Policy ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PolicyStore.class)
    public PolicyStore yamlPolicyStore(RateLimiterProperties properties) {
        return new YamlPolicyStore(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PolicyReloadService.class)
    public PolicyReloadService policyReloadService(PolicyStore policyStore, RateLimiterProperties properties) {
        return new PolicyReloadService(policyStore, properties);
    }

    @Bean
    @ConditionalOnMissingBean(PolicyReloadEndpoint.class)
    public PolicyReloadEndpoint policyReloadEndpoint(PolicyReloadService reloadService) {
        return new PolicyReloadEndpoint(reloadService);
    }

    @Bean
    @ConditionalOnMissingBean(PolicyResolver.class)
    public PolicyResolver policyResolver(PolicyReloadService reloadService) {
        return new PolicyResolver(reloadService);
    }

    // ── Engine ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(PolicyEvaluator.class)
    public PolicyEvaluator policyEvaluator(RateLimitBackend backend, Clock clock) {
        return new PolicyEvaluator(backend, clock);
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitEngine.class)
    public RateLimitEngine rateLimitEngine(RateLimiterProperties properties,
                                           PolicyResolver policyResolver,
                                           CompositeKeyBuilder keyBuilder,
                                           PolicyEvaluator policyEvaluator,
                                           RateLimitMetrics metrics) {
        return new RateLimitEngine(properties, policyResolver, keyBuilder, policyEvaluator, metrics);
    }

    // ── Filter ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(RateLimitFilter.class)
    public RateLimitFilter rateLimitFilter(RateLimitEngine engine,
                                           RateLimitMetrics metrics,
                                           ObjectMapper objectMapper,
                                           RateLimiterProperties properties) {
        return new RateLimitFilter(engine, metrics, objectMapper, properties);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitHeaderAdvice.class)
    public RateLimitHeaderAdvice rateLimitHeaderAdvice() {
        return new RateLimitHeaderAdvice();
    }

    // ── Annotation Support ───────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean(RateLimitInterceptor.class)
    public RateLimitInterceptor rateLimitInterceptor(RateLimiterProperties properties,
                                                      CompositeKeyBuilder keyBuilder,
                                                      PolicyEvaluator policyEvaluator,
                                                      ObjectMapper objectMapper) {
        return new RateLimitInterceptor(properties, keyBuilder, policyEvaluator, objectMapper);
    }

    @Bean
    public WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
