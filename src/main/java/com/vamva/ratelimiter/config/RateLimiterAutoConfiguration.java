package com.vamva.ratelimiter.config;

import com.vamva.ratelimiter.backend.InMemoryBackend;
import com.vamva.ratelimiter.backend.RateLimitBackend;
import com.vamva.ratelimiter.backend.RedisBackend;
import com.vamva.ratelimiter.metrics.RateLimitMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for the rate limiter components.
 *
 * <p>Selects the appropriate {@link RateLimitBackend} bean based on the
 * {@code rate-limiter.backend} property (defaults to "redis").</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
@EnableScheduling
public class RateLimiterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "rate-limiter.backend", havingValue = "redis", matchIfMissing = true)
    public RateLimitBackend redisBackend(StringRedisTemplate redisTemplate,
                                         RateLimiterProperties properties,
                                         RateLimitMetrics metrics) {
        return new RedisBackend(redisTemplate, properties.isFailOpen(), metrics);
    }

    @Bean
    @ConditionalOnProperty(name = "rate-limiter.backend", havingValue = "in-memory")
    public RateLimitBackend inMemoryBackend() {
        return new InMemoryBackend();
    }
}
