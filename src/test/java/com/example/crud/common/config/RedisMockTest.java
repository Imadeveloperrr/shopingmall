package com.example.crud.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;


@TestConfiguration
class TestRedisConfig {
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}

@SpringBootTest(classes = {RedisConfig.class, TestRedisConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
@ActiveProfiles("test")
@EnableCaching
@Testcontainers
public class RedisMockTest {

    @Autowired
    StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();

    @Autowired
    private CacheManager cacheManager;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.redis.database", () -> "0");
        registry.add("spring.redis.timeout", () -> "2000");
    }

    @Test
    void testRedisBasicConnection() {
        // given
        String testKey = "test:redis" + System.currentTimeMillis();
        String testValue = "TestValue";

        // when
        stringRedisTemplate.opsForValue().set(testKey, testValue);

        // then
        String resultValue = stringRedisTemplate.opsForValue().get(testKey);
        assertEquals(testValue, resultValue, "Redis 기본 연결 테스트 실패.");

        // cleanUp
        stringRedisTemplate.delete(testKey);
    }

    @Test
    void testCacheManagerConfiguration() {
        // given

    }

}