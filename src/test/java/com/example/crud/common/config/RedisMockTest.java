package com.example.crud.common.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
public class RedisMockTest {

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
        var cachedValue = embeddingCache.get(cacheKey, float[].class);
        assertNotNull(cachedValue, "캐시된 임베딩이 null입니다");
        assertArrayEquals(testEmbedding, cachedValue, "캐시된 임베딩이 일치하지 않습니다");

        // Cleanup
        embeddingCache.evict(cacheKey);
        log.info("임베딩 캐시 동작 테스트 성공");
    }
}