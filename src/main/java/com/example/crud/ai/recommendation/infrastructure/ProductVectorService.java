package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.example.crud.ai.common.VectorFormatter;
import java.util.*;

import static com.example.crud.common.utility.NativeQueryResultExtractor.*;

/**
 * 벡터 기반 상품 매칭 서비스 - 간소화된 버전
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductVectorService {

    private final ProductRepository productRepository;
    private final EmbeddingApiClient embeddingApiClient;

    public List<ProductSimilarity> findSimilarProducts(String queryText, int limit) {
        log.info("🔍 상품 유사도 검색 시작: 쿼리='{}', limit={}, threshold=0.3", queryText, limit);

        try {
            // embeddingApiClient에서 널값 예외처리.
            float[] queryVector = embeddingApiClient.generateEmbedding(queryText);
            log.info("✅ 임베딩 벡터 생성 성공: 차원={}", queryVector.length);

            // 벡터를 PostgreSQL 형식으로 변환
            String vectorString = VectorFormatter.formatForPostgreSQL(queryVector);
            log.debug("🔄 벡터 문자열 변환 완료: 길이={}", vectorString.length());

            // 동적 임계값으로 유연한 검색 (높은 품질부터 시도)
            List<Object[]> results = findWithDynamicThreshold(vectorString, limit);

            List<ProductSimilarity> similarities = new ArrayList<>();
            for (Object[] row : results) {
                try {
                    Long productId = extractLong(row[0], "productId");
                    String productName = extractString(row[1], "productName");
                    String description = extractString(row[2], "description");
                    Double similarity = extractDouble(row[3], "similarity");

                    similarities.add(new ProductSimilarity(
                        productId, similarity, productName, description
                    ));
                    log.debug("🎯 상품 매칭: id={}, 유사도={}, 상품명='{}'", productId, String.format("%.4f", similarity), productName);
                } catch (Exception e) {
                    log.warn("상품 데이터 변환 실패, 해당 상품 건너뜀: {}", Arrays.toString(row), e);
                    // 해당 상품만 건너뛰고 계속 진행
                }
            }

            // 결과 분석 및 로깅
            if (similarities.isEmpty()) {
                log.warn("⚠️ 빈 결과 발생! 원인 분석:");
                log.warn("  - 쿼리: '{}'", queryText);
                log.warn("  - 임계값: 0.3 (30% 이상 유사도)");
                log.warn("  - SQL 결과 개수: {}", results.size());
                log.warn("  💡 해결방안: 임계값을 낮추거나 상품 데이터 확인 필요");
            } else {
                double maxSimilarity = similarities.stream().mapToDouble(ProductSimilarity::similarity).max().orElse(0.0);
                double minSimilarity = similarities.stream().mapToDouble(ProductSimilarity::similarity).min().orElse(0.0);
                log.info("✅ 추천 완료: {}개 상품, 유사도 범위 {:.4f}~{:.4f}", similarities.size(), minSimilarity, maxSimilarity);
            }

            return similarities;

        } catch (NullPointerException e) {
            log.error("검색어가 null입니다: {}", queryText, e);
            throw new IllegalArgumentException("검색어가 비어있습니다.", e);
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.error("OpenAI API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("AI 서비스에 일시적 문제가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("데이터베이스 접근 오류: {}", e.getMessage(), e);
            throw new RuntimeException("데이터 조회 중 문제가 발생했습니다. 관리자에게 문의해주세요.", e);
        } catch (NumberFormatException e) {
            log.error("벡터 데이터 변환 오류: {}", e.getMessage(), e);
            throw new RuntimeException("상품 데이터 형식에 문제가 있습니다.", e);
        } catch (IllegalArgumentException e) {
            // 이미 처리된 예외는 다시 던지기
            throw e;
        } catch (Exception e) {
            log.error("예상치 못한 오류 발생 - 쿼리: '{}', 상세: {}", queryText, e.getMessage(), e);
            throw new RuntimeException("상품 추천 중 예상치 못한 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    public List<ProductSimilarity> findSimilarProductsByProduct(Long productId, int limit) {
        try {

            Product targetProduct = productRepository.findById(productId).orElseThrow();
            String vectorString = targetProduct.getDescriptionVector();

            if (vectorString == null || vectorString.isEmpty()) {
                throw new IllegalArgumentException("대상 상품에 임베딩 벡터가 없습니다.");
            }

            // PostgreSQL에서 직접 유사도 검색 수행 (더 높은 임계값 사용)
            List<Object[]> results = productRepository.findSimilarProductsByVector(
                vectorString, 0.4, limit
            );

            List<ProductSimilarity> similarities = new ArrayList<>();
            for (Object[] row : results) {
                try {
                    Long currentProductId = extractLong(row[0], "productId");

                    // 자기 자신은 제외
                    if (!currentProductId.equals(productId)) {
                        String productName = extractString(row[1], "productName");
                        String description = extractString(row[2], "description");
                        Double similarity = extractDouble(row[3], "similarity");

                        similarities.add(new ProductSimilarity(
                            currentProductId, similarity, productName, description
                        ));
                    }
                } catch (Exception e) {
                    log.warn("상품 데이터 변환 실패, 해당 상품 건너뜀: {}", Arrays.toString(row), e);
                }
            }

            return similarities;

        } catch (Exception e) {
            throw new RuntimeException("상품과 상품간의 유사도 검색 중 오류 발생", e);
        }
    }

    /**
     * 동적 임계값으로 상품 검색 (높은 품질부터 낮은 품질 순으로 시도)
     */
    private List<Object[]> findWithDynamicThreshold(String vectorString, int limit) {
        // 더 높은 품질의 추천을 위한 임계값 (0.3 = 30% 이상)
        double[] thresholds = {0.5, 0.4, 0.3};

        log.debug("동적 임계값 검색 시작: 최대 {}개 상품 검색", limit);

        for (double threshold : thresholds) {
            List<Object[]> results = productRepository.findSimilarProductsByVector(
                vectorString, threshold, limit
            );

            if (!results.isEmpty()) {
                log.info("임계값 {}에서 {}개 상품 발견", threshold, results.size());
                return results;
            }
        }

        // 임계값 실패 시: 최소 임계값(0.25)으로 재시도
        log.warn("표준 임계값에서 결과 없음. 최소 임계값 0.25로 재시도");

        List<Object[]> finalResults = productRepository.findSimilarProductsByVector(vectorString, 0.25, limit);

        if (finalResults.isEmpty()) {
            log.warn("유사 상품을 찾을 수 없습니다. (임계값 0.25 이상)");
        }

        return finalResults;
    }

    /**
     * 카테고리별 유사 상품 검색
     */
    public List<ProductSimilarity> findSimilarProductsByCategory(String queryText, String category, int limit) {
        log.info("🔍 카테고리별 상품 검색 시작: 쿼리='{}', 카테고리='{}', limit={}", queryText, category, limit);

        try {
            float[] queryVector = embeddingApiClient.generateEmbedding(queryText);
            String vectorString = VectorFormatter.formatForPostgreSQL(queryVector);

            // 카테고리별 임계값 조정 (카테고리 내 검색은 더 낮은 임계값 사용)
            double[] thresholds = {0.3, 0.2, 0.1, 0.05};

            for (double threshold : thresholds) {
                List<Object[]> results = productRepository.findSimilarProductsByVectorAndCategory(
                    vectorString, category, threshold, limit
                );

                if (!results.isEmpty()) {
                    log.info("✅ 카테고리 {} 임계값 {}에서 {}개 상품 발견", category, threshold, results.size());
                    return convertToSimilarities(results);
                }
            }

            log.warn("⚠️ 카테고리 {}에서 유사 상품 찾기 실패", category);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("카테고리별 검색 오류: category={}", category, e);
            return Collections.emptyList();
        }
    }

    /**
     * 다중 카테고리 검색 (멀티서치)
     */
    public Map<String, List<ProductSimilarity>> findSimilarProductsMultiCategory(
            String queryText, List<String> categories, int limitPerCategory) {

        log.info("🔍 다중 카테고리 검색: 쿼리='{}', 카테고리={}", queryText, categories);
        Map<String, List<ProductSimilarity>> results = new HashMap<>();

        for (String category : categories) {
            List<ProductSimilarity> categoryResults = findSimilarProductsByCategory(
                queryText, category, limitPerCategory
            );
            if (!categoryResults.isEmpty()) {
                results.put(category, categoryResults);
            }
        }

        log.info("✅ 다중 카테고리 검색 완료: {}개 카테고리에서 결과 발견", results.size());
        return results;
    }

    /**
     * Object[] 결과를 ProductSimilarity 리스트로 변환
     */
    private List<ProductSimilarity> convertToSimilarities(List<Object[]> results) {
        List<ProductSimilarity> similarities = new ArrayList<>();

        for (Object[] row : results) {
            try {
                Long productId = extractLong(row[0], "productId");
                String productName = extractString(row[1], "productName");
                String description = extractString(row[2], "description");
                Double similarity = extractDouble(row[3], "similarity");

                similarities.add(new ProductSimilarity(
                    productId, similarity, productName, description
                ));
            } catch (Exception e) {
                log.warn("상품 데이터 변환 실패: {}", Arrays.toString(row), e);
            }
        }

        return similarities;
    }

    public record ProductSimilarity(
            Long productId,
            double similarity,
            String productName,
            String description) {
    }
}


/*

  1. findAll() 개선 방법

  현재 문제:
  List<Product> products = productRepository.findAll(); // 10만개 로딩!

  개선 방법들:
  // A. 벡터가 있는 상품만 조회
  @Query("SELECT p FROM Product p WHERE p.descriptionVector IS NOT NULL")
  List<Product> findAllWithVectors();

  // B. 스트리밍 처리 (메모리 절약)
  @Query("SELECT p FROM Product p WHERE p.descriptionVector IS NOT NULL")
  Stream<Product> streamAllWithVectors();

  // C. 페이징 처리
  Pageable pageable = PageRequest.of(0, 1000);
  Page<Product> products = productRepository.findAllWithVectors(pageable);

  2. 반복문 내부 최적화

  현재 비효율:
  for (Product product : products) {
      if (!product.getNumber().equals(productId) &&
  product.getDescriptionVector() != null) {
          double similarity = embeddingApiClient.calculateSimilarity(...);
  // 매번 메서드 호출
          if (similarity > 0.3) { ... }
      }
  }

  최적화 방법들:

  A. Early Exit 패턴:
  // 상위 N개만 찾으면 더 이상 계산 안함
  if (similarities.size() >= limit * 2) break; // 버퍼 두고 조기 종료

  B. 배치 벡터 연산 (CPU 집약적 작업):
  // 한 번에 여러 벡터 비교 (SIMD 활용)
  float[][] allVectors = products.stream()...;
  double[] allSimilarities = calculateBatchSimilarity(queryVector,
  allVectors);

  C. 병렬 처리:
  similarities = products.parallelStream()
      .filter(p -> !p.getNumber().equals(productId))
      .filter(p -> p.getDescriptionVector() != null)
      .mapToDouble(p -> calculateSimilarity(...))
      .filter(sim -> sim > 0.3)
      .collect(...);

  3. 알고리즘 레벨 최적화

  현재: O(N) - 전체 스캔
  개선:
  - 근사 최근접 이웃 (ANN): Faiss, Annoy 라이브러리
  - pgvector: PostgreSQL에서 직접 유사도 검색
  - 캐싱: Redis에 인기 상품 유사도 미리 계산

======================== pgvector를 이용한 유사도 최적화 ========================


● pgvector를 활용한 SQL 레벨 최적화!

  현재 vs pgvector 방식 비교

  현재 방식 (Java에서 처리):
  // 1. 모든 상품을 메모리로 로딩 (10만개)
  List<Product> products = productRepository.findAll();

  // 2. Java에서 1:1 비교 (10만번 반복)
  for (Product product : products) {
      double similarity =
  embeddingApiClient.calculateSimilarity(queryVector, product.getVector());
  }
  시간 복잡도: O(N) × 벡터연산 = 10초~30초

  pgvector 방식 (SQL에서 처리):
  -- PostgreSQL이 직접 벡터 연산 수행
  SELECT product_id, name, description,
         1 - (description_vector <=> ?) AS similarity
  FROM product
  WHERE product_id != ?
    AND description_vector IS NOT NULL
    AND 1 - (description_vector <=> ?) > 0.4
  ORDER BY description_vector <=> ?
  LIMIT 10;
  시간 복잡도: O(log N) (인덱스 활용) = 100ms~500ms

  성능 개선 효과

  속도: 50-100배 빨라짐
  메모리: Java 힙 메모리 절약 (10만개 객체 안 만듦)
  CPU: PostgreSQL C 레벨 최적화 활용

  Repository 구현 방법:

  @Query(value = """
      SELECT p.number as productId, p.name as productName,
             p.description, (1 - (p.description_vector <=> :queryVector)) as
   similarity
      FROM Product p
      WHERE p.number != :excludeId
        AND p.description_vector IS NOT NULL
        AND (1 - (p.description_vector <=> :queryVector)) > :threshold
      ORDER BY p.description_vector <=> :queryVector
      LIMIT :limit
      """, nativeQuery = true)
  List<ProductSimilarityProjection> findSimilarByVector(
      @Param("queryVector") String queryVector,
      @Param("excludeId") Long excludeId,
      @Param("threshold") double threshold,
      @Param("limit") int limit
  );

  단점: SQL 복잡, 벡터 직렬화 필요
 */