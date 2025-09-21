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
@Testcontainers
@EnableCaching
@ActiveProfiles("test")
@Slf4j
public class RedisMockTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.redis.database", () -> "0");
        registry.add("spring.redis.timeout", () -> "2000");
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void testRedisBasicConnection() {
        // Given
        String testKey = "test:cache:" + System.currentTimeMillis();
        String testValue = "TestValue123";

        // When
        stringRedisTemplate.opsForValue().set(testKey, testValue);

        // Then
        String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
        assertEquals(testValue, retrievedValue, "Redis 기본 연결 테스트 실패");

        // Cleanup
        stringRedisTemplate.delete(testKey);
        log.info("Redis 기본 연결 테스트 성공");
    }

    @Test
    void testCacheManagerConfiguration() {
        // Given
        assertNotNull(cacheManager, "CacheManager가 주입되지 않았습니다");

        // When
        var embeddingCache = cacheManager.getCache("embeddings");

        // Then
        assertNotNull(embeddingCache, "embeddings 캐시가 설정되지 않았습니다");
        log.info("CacheManager 설정 테스트 성공 - embeddings 캐시 확인");
    }

    @Test
    void testEmbeddingCacheOperation() {
        // Given
        var embeddingCache = cacheManager.getCache("embeddings");
        String cacheKey = "test-embedding-key";
        float[] testEmbedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};

        // When - 캐시에 저장
        embeddingCache.put(cacheKey, testEmbedding);

        // Then - 캐시에서 조회
        var cachedValueWrapper = embeddingCache.get(cacheKey);
        assertNotNull(cachedValueWrapper, "캐시된 값이 null입니다");

        Object cachedValue = cachedValueWrapper.get();
        assertNotNull(cachedValue, "캐시된 임베딩이 null입니다");

        // 디버깅: 실제 타입 확인
        log.info("캐시된 값의 타입: {}", cachedValue.getClass().getName());
        log.info("캐시된 값: {}", cachedValue);

        // Jackson으로 직렬화 후 역직렬화되면서 ArrayList로 변환될 수 있음
        if (cachedValue instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<Number> list = (java.util.List<Number>) cachedValue;
            float[] cachedEmbedding = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                cachedEmbedding[i] = list.get(i).floatValue();
            }
            assertArrayEquals(testEmbedding, cachedEmbedding, "캐시된 임베딩이 일치하지 않습니다");
        } else if (cachedValue instanceof float[]) {
            float[] cachedEmbedding = (float[]) cachedValue;
            assertArrayEquals(testEmbedding, cachedEmbedding, "캐시된 임베딩이 일치하지 않습니다");
        } else {
            fail("캐시된 값이 예상된 타입이 아닙니다. 실제 타입: " + cachedValue.getClass().getName());
        }

        // Cleanup
        embeddingCache.evict(cacheKey);
        log.info("임베딩 캐시 동작 테스트 성공");
    }
}