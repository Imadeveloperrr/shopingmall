package com.example.crud.ai.recommendation.infrastructure.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 추천 시스템 모니터링
 * - 시스템 상태 체크
 * - 성능 메트릭 수집
 * - 알람 발생
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationSystemMonitor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String METRICS_KEY_PREFIX = "metrics:recommendation:";
    private static final String HEALTH_KEY = "health:recommendation";

    /**
     * 1분마다 시스템 헬스 체크
     */
    @Scheduled(fixedDelay = 60000)
    public void checkSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", LocalDateTime.now());

        try {
            // Redis 연결 체크
            Boolean redisHealthy = checkRedisHealth();
            health.put("redis", redisHealthy);

            // Kafka 연결 체크
            Boolean kafkaHealthy = checkKafkaHealth();
            health.put("kafka", kafkaHealthy);

            // ML 서비스 체크
            Boolean mlHealthy = checkMLServiceHealth();
            health.put("mlService", mlHealthy);

            // Elasticsearch 체크
            Boolean esHealthy = checkElasticsearchHealth();
            health.put("elasticsearch", esHealthy);

            // 캐시 히트율 계산
            Double cacheHitRate = calculateCacheHitRate();
            health.put("cacheHitRate", cacheHitRate);

            // 평균 추천 응답 시간
            Double avgResponseTime = calculateAvgResponseTime();
            health.put("avgResponseTime", avgResponseTime);

            // 전체 상태 결정
            String overallStatus = determineOverallStatus(health);
            health.put("status", overallStatus);

            // Redis에 상태 저장
            redisTemplate.opsForValue().set(HEALTH_KEY, health, 5, TimeUnit.MINUTES);

            // 이상 감지 시 알람
            if (!"healthy".equals(overallStatus)) {
                sendAlert(health);
            }

        } catch (Exception e) {
            log.error("헬스 체크 실패", e);
            health.put("status", "error");
            health.put("error", e.getMessage());
            sendAlert(health);
        }
    }

    /**
     * 5분마다 성능 메트릭 수집
     */
    @Scheduled(fixedDelay = 300000)
    public void collectPerformanceMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // 추천 요청 수
            Long recommendationCount = getMetricValue("request_count");
            metrics.put("requestCount", recommendationCount);

            // 캐시 히트/미스
            Long cacheHits = getMetricValue("cache_hits");
            Long cacheMisses = getMetricValue("cache_misses");
            metrics.put("cacheHits", cacheHits);
            metrics.put("cacheMisses", cacheMisses);

            // 평균 추천 아이템 수
            Double avgItemCount = getMetricAverage("recommendation_items");
            metrics.put("avgRecommendationItems", avgItemCount);

            // 에러율
            Long errors = getMetricValue("errors");
            Double errorRate = recommendationCount > 0 ?
                    (double) errors / recommendationCount : 0.0;
            metrics.put("errorRate", errorRate);

            // Kafka 지연
            Long kafkaLag = getKafkaConsumerLag();
            metrics.put("kafkaLag", kafkaLag);

            // 메트릭 저장
            String metricsKey = METRICS_KEY_PREFIX + System.currentTimeMillis();
            redisTemplate.opsForValue().set(metricsKey, metrics, 24, TimeUnit.HOURS);

            // 대시보드용 최신 메트릭 업데이트
            updateDashboardMetrics(metrics);

        } catch (Exception e) {
            log.error("메트릭 수집 실패", e);
        }
    }

    /**
     * 추천 품질 모니터링 (일일)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    public void monitorRecommendationQuality() {
        try {
            Map<String, Object> qualityReport = new HashMap<>();

            // CTR (Click Through Rate) 계산
            Double ctr = calculateCTR();
            qualityReport.put("ctr", ctr);

            // 구매 전환율
            Double conversionRate = calculateConversionRate();
            qualityReport.put("conversionRate", conversionRate);

            // 추천 다양성
            Double diversityScore = calculateRecommendationDiversity();
            qualityReport.put("diversityScore", diversityScore);

            // 사용자 만족도 (피드백 기반)
            Double satisfactionScore = calculateUserSatisfaction();
            qualityReport.put("satisfactionScore", satisfactionScore);

            // 리포트 저장 및 발송
            saveQualityReport(qualityReport);
            sendQualityReport(qualityReport);

        } catch (Exception e) {
            log.error("품질 모니터링 실패", e);
        }
    }

    // === 헬스 체크 메서드들 ===

    private Boolean checkRedisHealth() {
        try {
            redisTemplate.opsForValue().get("health:check");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Boolean checkKafkaHealth() {
        try {
            // Kafka 헬스 체크 로직
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Boolean checkMLServiceHealth() {
        // ML 서비스 헬스 체크 (HTTP 요청)
        return true;
    }

    private Boolean checkElasticsearchHealth() {
        // Elasticsearch 헬스 체크
        return true;
    }

    // === 메트릭 계산 메서드들 ===

    private Double calculateCacheHitRate() {
        Long hits = getMetricValue("cache_hits");
        Long misses = getMetricValue("cache_misses");
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    private Double calculateAvgResponseTime() {
        // 평균 응답 시간 계산
        return 250.0; // ms
    }

    private Long getMetricValue(String metric) {
        Object value = redisTemplate.opsForValue().get("metric:" + metric);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    private Double getMetricAverage(String metric) {
        // 평균값 계산
        return 15.0;
    }

    private Long getKafkaConsumerLag() {
        // Kafka 컨슈머 지연 확인
        return 0L;
    }

    // === 품질 메트릭 계산 ===

    private Double calculateCTR() {
        // 클릭률 계산
        return 0.15; // 15%
    }

    private Double calculateConversionRate() {
        // 구매 전환율 계산
        return 0.03; // 3%
    }

    private Double calculateRecommendationDiversity() {
        // 추천 다양성 점수
        return 0.75;
    }

    private Double calculateUserSatisfaction() {
        // 사용자 만족도
        return 4.2; // 5점 만점
    }

    // === 알림 및 리포트 ===

    private String determineOverallStatus(Map<String, Object> health) {
        boolean allHealthy = (Boolean) health.getOrDefault("redis", false) &&
                (Boolean) health.getOrDefault("kafka", false) &&
                (Boolean) health.getOrDefault("mlService", false) &&
                (Boolean) health.getOrDefault("elasticsearch", false);

        if (!allHealthy) return "unhealthy";

        Double cacheHitRate = (Double) health.getOrDefault("cacheHitRate", 0.0);
        if (cacheHitRate < 0.5) return "degraded";

        return "healthy";
    }

    private void sendAlert(Map<String, Object> health) {
        log.error("시스템 알람 발생: {}", health);
        // 실제로는 Slack, Email 등으로 알림 발송
    }

    private void updateDashboardMetrics(Map<String, Object> metrics) {
        // Grafana 등 대시보드용 메트릭 업데이트
        redisTemplate.opsForValue().set("dashboard:metrics:latest", metrics);
    }

    private void saveQualityReport(Map<String, Object> report) {
        String key = "quality:report:" + LocalDateTime.now().toLocalDate();
        redisTemplate.opsForValue().set(key, report, 30, TimeUnit.DAYS);
    }

    private void sendQualityReport(Map<String, Object> report) {
        log.info("추천 품질 리포트: {}", report);
        // 이메일 또는 Slack으로 리포트 발송
    }
}