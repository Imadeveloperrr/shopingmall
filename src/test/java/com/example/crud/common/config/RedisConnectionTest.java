package com.example.crud.common.config;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@Slf4j
public class RedisConnectionTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg15").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("init-pgvector.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis 설정
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
        registry.add("spring.redis.database", () -> "0");
        registry.add("spring.redis.timeout", () -> "2000");

        // PostgreSQL 설정
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisConnection() {
        // Given
        String testKey = "test:redis:" + System.currentTimeMillis();
        String testValue = "TestValue";

        // When
        stringRedisTemplate.opsForValue().set(testKey, testValue);
        assertTrue(stringRedisTemplate.hasKey(testKey), "Redis에 키가 저장되지 않았습니다");

        // Then
        String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
        assertEquals(testValue, retrievedValue, "Redis에서 값을 제대로 가져오지 못했습니다.");

    }

    @AfterEach
    void cleanUp() {
        for (String keys : List.of("test:redis:*")) {
            Set<String> deleteKey = stringRedisTemplate.keys(keys);
            if (!deleteKey.isEmpty()) {
                stringRedisTemplate.delete(deleteKey);
            }
        }
        log.info("Redis 테스트 키 정리 완료");
    }
}
