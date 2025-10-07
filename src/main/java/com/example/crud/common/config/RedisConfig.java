package com.example.crud.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring의 작동 방식:
 *   1. Spring 시작
 *      ↓
 *   2. redisConnectionFactory() 실행 → RedisConnectionFactory 객체 생성
 *      ↓
 *   3. stringRedisTemplate(connectionFactory) 실행
 *      → Spring이 자동으로 2번의 결과를 파라미터로 주입
 *      ↓
 *   4. redisTemplate(connectionFactory, ...) 실행
 *      → Spring이 자동으로 2번의 결과를 파라미터로 주입
 *      ↓
 *   5. cacheManager(connectionFactory, ...) 실행
 *      → Spring이 자동으로 2번의 결과를 파라미터로 주입
 *
 *   즉, RedisConnectionFactory는:
 *   - 직접 사용되지 않음
 *   - 다른 Bean을 만들 때 재료로 사용됨
 *   - Redis 서버 연결 정보를 담고 있는 "설계도" 역할
 */

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${spring.redis.host:redis}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.timeout:2000}")
    private int timeout;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * Redis 연결 팩토리 구성
     * Lettuce 클라이언트 사용 (비동기 및 스레드 안전한 Redis 클라이언트)
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setDatabase(database); // Redis는 16개 DB 제공 (0~15)

        // 연결 풀 설정
        /**
         * 미리 연결을 만들어 두고 재사용. (Tcp Handshake 비용)
         *  Connection Pool = 택시 승강장
         *
         *   Without Pool:
         *   사용자: "Redis에 저장해줘" → 새 택시 부름 → 운행 → 택시 해산
         *   사용자: "Redis에서 가져와" → 또 새 택시 부름 → 운행 → 택시 해산
         *
         *   With Pool:
         *   사용자: "Redis에 저장해줘" → 대기중인 택시 사용 → 운행 → 택시 다시 대기
         *   사용자: "Redis에서 가져와" → 대기중인 택시 사용 → 운행 → 택시 다시 대
         */
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(8); // 대기 8개
        poolConfig.setMinIdle(2); // 최소 2개
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMaxWait(Duration.ofMillis(2000));

        // Lettuce 클라이언트 구성
        /**
         * 비동기지원 : WebClient 처럼 non-blocking
         * Thread-safe : 여러 스레드가 동시 사용
         * Connection Pool : 자동 관리.
         */
        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .poolConfig(poolConfig)
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }


    /**
     * 문자열 데이터 처리를 위한 StringRedisTemplate
     * 일반적인 문자열 데이터 처리에 사용
     * Key : String, Value : String
     * 세션 토큰, 간단한 카운터 사용시 활용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    /**
     * 범용 객체 처리를 위한 RedisTemplate
     * 다양한 유형의 객체를 Redis에 저장할 때 사용
     * Key : String, Value : 모든 객체
     * 복잡한 객체 저장. ex EmbeddingGenerate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();

        return template;
    }

    /**
     * Redis 캐시 매니저 구성
     * @EnableCaching 애노테이션과 함께 사용
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.registerModule(new JavaTimeModule());

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);

        // 기본 캐시 설정 (30분 TTL) Time To Live
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        // 임베딩 캐시 설정 (24시간 TTL) Time To Live
        RedisCacheConfiguration embeddingCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        // 캐시별 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("embeddings", embeddingCacheConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}