package com.example.crud.ai.recommendation.infrastructure;

import com.example.crud.ai.embedding.EmbeddingApiClient;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

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
        try {
            // embeddingApiClient에서 널값 예외처리.
            float[] queryVector = embeddingApiClient.generateEmbedding(queryText);

            List<Product> products = productRepository.findAll(); // 병목 지점. ★★★★★

            List<ProductSimilarity> similarities = new ArrayList<>();

            // 이 부분도 비동기 처리나 최적화 해야함. ★★★★★
            for (Product product : products) {
                if (product.getDescriptionVector() != null) {
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

        } catch (NullPointerException e) {
            throw new IllegalArgumentException("검색어가 비어있습니다.", e);
        } catch (Exception e) {
            throw new RuntimeException("상품 유사도 검색 중 오류 발생", e);
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