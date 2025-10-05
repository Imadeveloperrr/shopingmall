package com.example.crud.repository;

import com.example.crud.entity.Product;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/*

  1. 코사인 유사도 (Cosine Similarity)

  범위: -1 ~ 1
  1에 가까울수록 = 더 유사함
  0 = 관련없음
  -1 = 완전히 반대

  2. 코사인 거리 (Cosine Distance)

  범위: 0 ~ 2
  0에 가까울수록 = 더 유사함  ← 이게 핵심!
  1 = 관련없음
  2 = 완전히 반대

 */

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByMember_Number(Long number);

    // 임베딩이 없는 상품 조회
    @Query("SELECT p FROM Product p WHERE p.descriptionVector IS NULL")
    List<Product> findByDescriptionVectorIsNull();

    // ProductEmbeddingService에서 사용하는 메서드 별칭
    default List<Product> findProductsWithoutEmbedding() {
        return findByDescriptionVectorIsNull();
    }

    // docker compose exec test-db psql -U sungho -d app_test

    /**
     *
     * @param queryVector
     * @param threshold
     * @param limit
     * @return
     *
     * CREATE INDEX product_vector_ivfflat_idx
     * ON product
     * USING ivfflat (description_vector vector_cosine_ops)
     * WITH (lists = 100);
     * IVFFlat 인덱스 추가.
     * Full Scan: 5초
     * IVFFlat: 50ms (100배 빠름)
     * HNSW: 100ms (50배 빠름
     */
    // 벡터 유사도 검색을 위한 네이티브 쿼리 (코사인 유사도) - TEXT에서 vector로 CAST
    @Query(value = """
            SELECT
            p.number as productId,
            p.name as productName,
            p.description as description,
            (1 - (p.description_vector <=> :queryVector::vector)) as similarity
        FROM product p
        WHERE p.description_vector IS NOT NULL
        AND (p.description_vector <=> :queryVector::vector) < (1 - :threshold)
        ORDER BY p.description_vector <=> :queryVector::vector
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarProductsByVector(
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    // 벡터 업데이트를 위한 네이티브 쿼리 -
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE product
        SET description_vector = :vectorString::vector
        WHERE number = :productId
        """, nativeQuery = true)
    int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);

    // 이메일로 조회
    List<Product> findByMember_Email(String email);

}