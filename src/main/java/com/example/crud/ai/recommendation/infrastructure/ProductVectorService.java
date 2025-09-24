package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.Locale;

/**
 * 벡터 기반 상품 매칭 서비스 - 간소화된 버전
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductVectorService {

    private final ProductRepository productRepository;
    private final EmbeddingApiClient embeddingApiClient;

    // PostgreSQL 벡터 포맷용 DecimalFormat (성능상 static으로 한 번만 생성)
    private static final DecimalFormat VECTOR_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        VECTOR_FORMAT = new DecimalFormat("0.########", symbols); // 소수점 8자리, 과학적 표기법 없음
        VECTOR_FORMAT.setGroupingUsed(false);
    }

    public List<ProductSimilarity> findSimilarProducts(String queryText, int limit) {
        log.info("🔍 상품 유사도 검색 시작: 쿼리='{}', limit={}, threshold=0.3", queryText, limit);

        try {
            // embeddingApiClient에서 널값 예외처리.
            float[] queryVector = embeddingApiClient.generateEmbedding(queryText);
            log.info("✅ 임베딩 벡터 생성 성공: 차원={}", queryVector.length);

            // 벡터를 PostgreSQL 형식으로 변환
            String vectorString = formatVectorForPostgreSQL(queryVector);
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
                    log.debug("🎯 상품 매칭: id={}, 유사도={:.4f}, 상품명='{}'", productId, similarity, productName);
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
            float[] queryVector = targetProduct.getDescriptionVector();

            List<Product> products = productRepository.findAll(); // 병목 지점. ★★★★★
            List<ProductSimilarity> similarities = new ArrayList<>();
            for (Product product : products) { // 모든 상품을 일일히 비교하는중 개선 필요.
                if (!product.getNumber().equals(productId) && product.getDescriptionVector() != null) {
                    double similarity = embeddingApiClient.calculateSimilarity(queryVector, product.getDescriptionVector());
                    if (similarity > 0.3) {
                        similarities.add(new ProductSimilarity(
                                product.getNumber(),
                                similarity,
                                product.getName(),
                                product.getDescription()
                        ));
                    }
                }
            }

            return similarities.stream()
                    .sorted(Comparator.comparingDouble(ProductSimilarity::similarity).reversed())
                    .limit(limit)
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("상품과 상품간의 유사도 검색 중 오류 발생", e);
        }
    }

    /**
     * 동적 임계값으로 상품 검색 (높은 품질부터 낮은 품질 순으로 시도)
     * 단일 상품 시나리오에 최적화: 더 낮은 임계값까지 확장하여 결과 보장
     */
    private List<Object[]> findWithDynamicThreshold(String vectorString, int limit) {
        // 확장된 임계값: 단일 상품에서도 결과를 찾을 수 있도록 더 낮은 값 포함
        double[] thresholds = {0.4, 0.3, 0.2, 0.1, 0.05, 0.02, 0.01};

        log.info("🎯 동적 임계값 검색 시작: 최대 {}개 상품 검색", limit);

        for (double threshold : thresholds) {
            List<Object[]> results = productRepository.findSimilarProductsByVector(
                vectorString, threshold, limit
            );
            log.info("📊 임계값 {} 결과: {}개 상품 발견", threshold, results.size());

            if (!results.isEmpty()) {
                log.info("✅ 임계값 {}에서 {}개 상품 추천 성공!", threshold, results.size());
                return results;
            }
        }

        // 모든 임계값 실패 시: 임계값 제거하고 유사도 순으로 상위 N개 반환
        log.warn("⚠️ 모든 임계값({} ~ {})에서 결과 없음!", thresholds[0], thresholds[thresholds.length-1]);
        log.info("🔄 최후 수단: 임계값 없이 유사도 순으로 상위 {}개 반환", limit);

        List<Object[]> finalResults = productRepository.findSimilarProductsByVector(vectorString, 0.0, limit);

        if (finalResults.isEmpty()) {
            log.error("❌ 치명적 오류: 임계값 0.0에서도 결과 없음 (데이터 또는 벡터 문제 의심)");
        } else {
            log.info("✅ 최후 수단 성공: {}개 상품 반환 (임계값 무시)", finalResults.size());
        }

        return finalResults;
    }

    /**
     * float[] 배열을 PostgreSQL vector 형식 문자열로 변환
     * - 과학적 표기법 방지 (1.234E-5 → 0.00001234)
     * - 고정 소수점 8자리 정밀도
     * - 성능 최적화를 위한 DecimalFormat 재사용
     */
    private String formatVectorForPostgreSQL(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 12); // 성능 최적화: 예상 크기로 초기화
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(VECTOR_FORMAT.format(vector[i])); // 고정 소수점 형식으로 변환
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Object[] 결과를 안전하게 Long으로 변환
     */
    private Long extractLong(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "이 null입니다.");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(fieldName + "이 숫자가 아닙니다: " + value.getClass());
    }

    /**
     * Object[] 결과를 안전하게 String으로 변환
     */
    private String extractString(Object value, String fieldName) {
        if (value == null) {
            return null; // description은 null일 수 있음
        }
        if (value instanceof String string) {
            return string;
        }
        return value.toString(); // 문자열 변환 시도
    }

    /**
     * Object[] 결과를 안전하게 Double로 변환
     */
    private Double extractDouble(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "이 null입니다.");
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(fieldName + "이 숫자가 아닙니다: " + value.getClass());
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