package com.example.crud.ai.embedding.application;

import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 상품 임베딩 생성 및 관리 서비스
 *
 * 개선 사항:
 * - 배치 처리 최적화
 * - 오류 처리 강화
 * - 진행 상황 추적
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${embedding.batch.size:50}")
    private int batchSize;

    @Value("${embedding.batch.delay:100}")
    private long batchDelay;

    private static final String BATCH_QUEUE_KEY = "embedding:batch:queue";
    private static final String PROGRESS_KEY = "embedding:progress";
    private static final String ERROR_KEY = "embedding:errors";

    /**
     * 단일 상품의 임베딩 생성 및 저장
     */
    @Transactional
    public void createAndSaveEmbedding(Product product) {
        try {
            // 이미 임베딩이 있으면 스킵
            if (product.getDescriptionVector() != null) {
                log.debug("상품 {}는 이미 임베딩이 있습니다.", product.getNumber());
                return;
            }

            // 임베딩 텍스트 생성
            String embeddingText = buildEmbeddingText(product);

            // ML 서비스를 통해 임베딩 생성
            float[] embedding = embeddingClient.embed(embeddingText)
                    .block(Duration.ofSeconds(5));

            if (embedding != null && embedding.length > 0) {
                product.setDescriptionVector(embedding);
                productRepository.save(product);
                log.info("임베딩 생성 완료: 상품 ID {}", product.getNumber());

                // 성공 카운트 증가
                incrementProgressCounter("success");
            } else {
                log.warn("임베딩 생성 실패 (빈 벡터): 상품 ID {}", product.getNumber());
                recordError(product.getNumber(), "Empty embedding vector");
            }
        } catch (Exception e) {
            log.error("임베딩 생성 중 오류 발생: 상품 ID {}", product.getNumber(), e);
            recordError(product.getNumber(), e.getMessage());
        }
    }

    /**
     * 비동기로 임베딩 생성 (상품 등록/수정 시)
     */
    @Async
    public void createAndSaveEmbeddingAsync(Long productId) {
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

            createAndSaveEmbedding(product);
        } catch (Exception e) {
            log.error("비동기 임베딩 생성 실패: productId={}", productId, e);
            recordError(productId, e.getMessage());
        }
    }

    /**
     * 배치로 임베딩 생성 (스케줄러)
     */
    @Scheduled(cron = "${embedding.batch.cron:0 0 3 * * *}") // 매일 새벽 3시
    @Transactional
    public void createMissingEmbeddings() {
        log.info("배치 임베딩 생성 시작");

        // 진행 상황 초기화
        initializeProgress();

        try {
            // 임베딩이 없는 상품 조회
            List<Product> productsWithoutEmbedding = productRepository.findByDescriptionVectorIsNull();
            int totalCount = productsWithoutEmbedding.size();

            if (totalCount == 0) {
                log.info("임베딩 생성이 필요한 상품이 없습니다.");
                return;
            }

            log.info("임베딩 생성 대상: {}개", totalCount);
            updateProgress("total", totalCount);

            // 배치 처리
            List<List<Product>> batches = createBatches(productsWithoutEmbedding, batchSize);

            for (int i = 0; i < batches.size(); i++) {
                List<Product> batch = batches.get(i);
                log.info("배치 {}/{} 처리 중 ({}개)", i + 1, batches.size(), batch.size());

                processBatch(batch);

                // API 부하 방지를 위한 딜레이
                if (i < batches.size() - 1) {
                    Thread.sleep(batchDelay);
                }
            }

            // 최종 결과 로깅
            logFinalResults();

        } catch (Exception e) {
            log.error("배치 임베딩 생성 실패", e);
            updateProgress("status", "FAILED");
        }
    }

    /**
     * 배치 처리
     */
    private void processBatch(List<Product> batch) {
        try {
            // 텍스트 추출
            List<String> texts = batch.stream()
                    .map(this::buildEmbeddingText)
                    .collect(Collectors.toList());

            // 배치 임베딩 생성
            List<float[]> embeddings = embeddingClient.batchEmbed(texts)
                    .block(Duration.ofSeconds(30));

            if (embeddings != null && embeddings.size() == batch.size()) {
                // 임베딩 저장
                for (int i = 0; i < batch.size(); i++) {
                    Product product = batch.get(i);
                    float[] embedding = embeddings.get(i);

                    if (embedding != null && embedding.length > 0) {
                        product.setDescriptionVector(embedding);
                        incrementProgressCounter("processed");
                        incrementProgressCounter("success");
                    } else {
                        recordError(product.getNumber(), "Empty embedding in batch");
                        incrementProgressCounter("failed");
                    }
                }

                // 배치 저장
                productRepository.saveAll(batch);
                log.info("배치 저장 완료: {}개", batch.size());

            } else {
                log.error("배치 임베딩 개수 불일치: expected={}, actual={}",
                        batch.size(), embeddings != null ? embeddings.size() : 0);

                // 개별 처리로 폴백
                for (Product product : batch) {
                    createAndSaveEmbedding(product);
                }
            }

        } catch (Exception e) {
            log.error("배치 처리 실패, 개별 처리로 전환", e);

            // 개별 처리
            for (Product product : batch) {
                try {
                    createAndSaveEmbedding(product);
                } catch (Exception ex) {
                    log.error("개별 상품 처리 실패: productId={}", product.getNumber(), ex);
                    recordError(product.getNumber(), ex.getMessage());
                }
            }
        }
    }

    /**
     * 상품 정보를 기반으로 임베딩용 텍스트 생성
     */
    private String buildEmbeddingText(Product product) {
        StringBuilder text = new StringBuilder();

        // 카테고리 정보
        text.append(product.getCategory().getGroupName()).append(" ");
        if (product.getSubCategory() != null) {
            text.append(product.getSubCategory()).append(" ");
        }

        // 브랜드와 상품명
        text.append(product.getBrand()).append(" ");
        text.append(product.getName()).append(" ");

        // 짧은 소개
        text.append(product.getIntro()).append(" ");

        // 상세 설명 (최대 500자)
        String desc = product.getDescription();
        if (desc != null) {
            if (desc.length() > 500) {
                desc = desc.substring(0, 500);
            }
            text.append(desc).append(" ");
        }

        // 색상과 사이즈 정보 추가
        if (!product.getProductOptions().isEmpty()) {
            text.append("색상: ");
            product.getProductOptions().stream()
                    .map(opt -> opt.getColor())
                    .distinct()
                    .forEach(color -> text.append(color).append(" "));

            text.append("사이즈: ");
            product.getProductOptions().stream()
                    .map(opt -> opt.getSize())
                    .distinct()
                    .forEach(size -> text.append(size).append(" "));
        }

        // 가격대 정보 (선택적)
        int price = product.getPrice();
        if (price < 30000) {
            text.append("저렴한 가격대 ");
        } else if (price < 100000) {
            text.append("중간 가격대 ");
        } else {
            text.append("프리미엄 가격대 ");
        }

        return text.toString().trim();
    }

    /**
     * 진행 상황 추적
     */
    private void initializeProgress() {
        redisTemplate.delete(PROGRESS_KEY);
        redisTemplate.delete(ERROR_KEY);

        redisTemplate.opsForHash().put(PROGRESS_KEY, "status", "RUNNING");
        redisTemplate.opsForHash().put(PROGRESS_KEY, "startTime", System.currentTimeMillis());
        redisTemplate.opsForHash().put(PROGRESS_KEY, "processed", 0);
        redisTemplate.opsForHash().put(PROGRESS_KEY, "success", 0);
        redisTemplate.opsForHash().put(PROGRESS_KEY, "failed", 0);

        redisTemplate.expire(PROGRESS_KEY, Duration.ofHours(24));
    }

    private void updateProgress(String field, Object value) {
        redisTemplate.opsForHash().put(PROGRESS_KEY, field, value);
    }

    private void incrementProgressCounter(String field) {
        redisTemplate.opsForHash().increment(PROGRESS_KEY, field, 1);
    }

    private void recordError(Long productId, String error) {
        String errorKey = ERROR_KEY + ":" + productId;
        redisTemplate.opsForValue().set(errorKey, error, 7, TimeUnit.DAYS);
        incrementProgressCounter("failed");
    }

    private void logFinalResults() {
        try {
            Object total = redisTemplate.opsForHash().get(PROGRESS_KEY, "total");
            Object success = redisTemplate.opsForHash().get(PROGRESS_KEY, "success");
            Object failed = redisTemplate.opsForHash().get(PROGRESS_KEY, "failed");
            Object startTime = redisTemplate.opsForHash().get(PROGRESS_KEY, "startTime");

            long duration = System.currentTimeMillis() - Long.parseLong(startTime.toString());

            log.info("배치 임베딩 생성 완료 - 전체: {}, 성공: {}, 실패: {}, 소요시간: {}초",
                    total, success, failed, duration / 1000);

            updateProgress("status", "COMPLETED");
            updateProgress("endTime", System.currentTimeMillis());
            updateProgress("duration", duration);

        } catch (Exception e) {
            log.error("최종 결과 로깅 실패", e);
        }
    }

    /**
     * 리스트를 배치로 분할
     */
    private <T> List<List<T>> createBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    /**
     * 진행 상황 조회 (API 엔드포인트용)
     */
    public Map<String, Object> getProgress() {
        Map<Object, Object> progress = redisTemplate.opsForHash().entries(PROGRESS_KEY);
        Map<String, Object> result = new HashMap<>();

        progress.forEach((k, v) -> result.put(k.toString(), v));

        // 진행률 계산
        if (result.containsKey("total") && result.containsKey("processed")) {
            int total = Integer.parseInt(result.get("total").toString());
            int processed = Integer.parseInt(result.get("processed").toString());
            if (total > 0) {
                double percentage = (double) processed / total * 100;
                result.put("percentage", String.format("%.2f", percentage));
            }
        }

        return result;
    }
}