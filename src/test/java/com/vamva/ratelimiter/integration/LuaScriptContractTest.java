package com.vamva.ratelimiter.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests that verify Lua script behavior against real Redis.
 * These tests validate the atomic guarantees of each script independently.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LuaScriptContractTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    class FixedWindowScript {

        private final DefaultRedisScript<List> script = new DefaultRedisScript<>();

        {
            script.setLocation(new ClassPathResource("scripts/fixed_window.lua"));
            script.setResultType(List.class);
        }

        @Test
        void firstCallReturnsOneAndSetsTtl() {
            String key = "contract:fw:1";
            List result = redisTemplate.execute(script, Collections.singletonList(key), "65");

            assertEquals(1L, ((Number) result.get(0)).longValue(), "Counter should be 1");
            long ttl = ((Number) result.get(1)).longValue();
            assertTrue(ttl > 0 && ttl <= 65, "TTL should be set");
        }

        @Test
        void incrementsAtomically() {
            String key = "contract:fw:2";

            for (int i = 1; i <= 5; i++) {
                List result = redisTemplate.execute(script, Collections.singletonList(key), "65");
                assertEquals((long) i, ((Number) result.get(0)).longValue());
            }
        }

        @Test
        void ttlOnlySetOnFirstCall() {
            String key = "contract:fw:3";

            // First call sets TTL
            redisTemplate.execute(script, Collections.singletonList(key), "65");
            Long ttl1 = redisTemplate.getExpire(key);

            // Second call should not reset TTL
            redisTemplate.execute(script, Collections.singletonList(key), "65");
            Long ttl2 = redisTemplate.getExpire(key);

            assertTrue(ttl2 <= ttl1, "TTL should not be reset on subsequent calls");
        }

        @Test
        void differentKeysAreIndependent() {
            redisTemplate.execute(script, Collections.singletonList("contract:fw:a"), "65");
            redisTemplate.execute(script, Collections.singletonList("contract:fw:a"), "65");
            List resultA = redisTemplate.execute(script, Collections.singletonList("contract:fw:a"), "65");

            List resultB = redisTemplate.execute(script, Collections.singletonList("contract:fw:b"), "65");

            assertEquals(3L, ((Number) resultA.get(0)).longValue());
            assertEquals(1L, ((Number) resultB.get(0)).longValue());
        }
    }

    @Nested
    class SlidingWindowScript {

        private final DefaultRedisScript<List> script = new DefaultRedisScript<>();

        {
            script.setLocation(new ClassPathResource("scripts/sliding_window.lua"));
            script.setResultType(List.class);
        }

        @Test
        void incrementsCurrentAndReadsPrevious() {
            String currentKey = "contract:sw:curr";
            String previousKey = "contract:sw:prev";

            // Pre-populate previous window
            redisTemplate.opsForValue().set(previousKey, "10");

            List result = redisTemplate.execute(script,
                    Arrays.asList(currentKey, previousKey), "65", "50");

            assertEquals(1L, ((Number) result.get(0)).longValue(), "Current should be 1");
            assertEquals(10L, ((Number) result.get(1)).longValue(), "Previous should be 10");
        }

        @Test
        void previousWindowMissingReturnsZero() {
            String currentKey = "contract:sw:curr2";
            String previousKey = "contract:sw:prev_nonexistent";

            List result = redisTemplate.execute(script,
                    Arrays.asList(currentKey, previousKey), "65", "50");

            assertEquals(1L, ((Number) result.get(0)).longValue());
            assertEquals(0L, ((Number) result.get(1)).longValue(), "Missing previous should be 0");
        }

        @Test
        void setsTtlOnCurrentWindow() {
            String currentKey = "contract:sw:curr3";
            String previousKey = "contract:sw:prev3";

            redisTemplate.execute(script, Arrays.asList(currentKey, previousKey), "65", "50");

            Long ttl = redisTemplate.getExpire(currentKey);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 65);
        }
    }

    @Nested
    class TokenBucketScript {

        private final DefaultRedisScript<List> script = new DefaultRedisScript<>();

        {
            script.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
            script.setResultType(List.class);
        }

        @Test
        void firstCallInitializesBucketAndConsumesOne() {
            String key = "contract:tb:1";
            long now = System.currentTimeMillis() / 1000;

            List result = redisTemplate.execute(script,
                    Collections.singletonList(key),
                    "10",           // capacity
                    "0.167",        // refill rate
                    String.valueOf(now),
                    "65");          // TTL

            assertEquals(1L, ((Number) result.get(0)).longValue(), "Should be allowed");
            assertEquals(9L, ((Number) result.get(1)).longValue(), "Remaining should be capacity-1");
            assertEquals(0L, ((Number) result.get(2)).longValue(), "No retry needed");
        }

        @Test
        void exhaustsBucketThenRejects() {
            String key = "contract:tb:2";
            long now = System.currentTimeMillis() / 1000;

            // Exhaust a 3-token bucket
            for (int i = 0; i < 3; i++) {
                List result = redisTemplate.execute(script,
                        Collections.singletonList(key),
                        "3", "0.05", String.valueOf(now), "65");
                assertEquals(1L, ((Number) result.get(0)).longValue(), "Request " + (i + 1) + " should be allowed");
            }

            // 4th should be rejected
            List result = redisTemplate.execute(script,
                    Collections.singletonList(key),
                    "3", "0.05", String.valueOf(now), "65");
            assertEquals(0L, ((Number) result.get(0)).longValue(), "Should be rejected");
            assertTrue(((Number) result.get(2)).longValue() > 0, "Should have retry_after > 0");
        }

        @Test
        void storesAsHash() {
            String key = "contract:tb:3";
            long now = System.currentTimeMillis() / 1000;

            redisTemplate.execute(script,
                    Collections.singletonList(key),
                    "10", "0.167", String.valueOf(now), "65");

            // Verify it's stored as hash with tokens and last_refill fields
            Object tokens = redisTemplate.opsForHash().get(key, "tokens");
            Object lastRefill = redisTemplate.opsForHash().get(key, "last_refill");

            assertNotNull(tokens, "tokens field should exist in hash");
            assertNotNull(lastRefill, "last_refill field should exist in hash");
        }

        @Test
        void hasTtlSet() {
            String key = "contract:tb:4";
            long now = System.currentTimeMillis() / 1000;

            redisTemplate.execute(script,
                    Collections.singletonList(key),
                    "10", "0.167", String.valueOf(now), "65");

            Long ttl = redisTemplate.getExpire(key);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 65);
        }
    }
}
