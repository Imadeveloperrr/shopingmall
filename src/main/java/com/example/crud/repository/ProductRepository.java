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

    // 카테고리별 유사 상품 검색을 위한 네이티브 쿼리
    @Query(value = """
        SELECT p.* FROM product p
        WHERE p.category = :category
        AND p.description_vector IS NOT NULL
        ORDER BY CAST(p.description_vector AS vector) <#> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Product> findSimilarProductsByCategory(
            @Param("category") String category,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit
    );

    // 벡터 유사도 검색을 위한 네이티브 쿼리 (코사인 유사도) - TEXT에서 vector로 CAST
    @Query(value = """
        SELECT
            p.number as productId,
            p.name as productName,
            p.description as description,
            (1 - (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))) as similarity
        FROM product p
        WHERE p.description_vector IS NOT NULL
        AND (1 - (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))) > :threshold
        ORDER BY (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarProductsByVector(
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    // 카테고리별 벡터 유사도 검색 (Intent Classification용)
    @Query(value = """
        SELECT
            p.number as productId,
            p.name as productName,
            p.description as description,
            (1 - (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))) as similarity
        FROM product p
        WHERE p.description_vector IS NOT NULL
        AND p.category = :category
        AND (1 - (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))) > :threshold
        ORDER BY (CAST(p.description_vector AS vector) <=> CAST(:queryVector AS vector))
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarProductsByVectorAndCategory(
            @Param("queryVector") String queryVector,
            @Param("category") String category,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    // 벡터 업데이트를 위한 네이티브 쿼리 - TEXT로 저장
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE product
        SET description_vector = :vectorString
        WHERE number = :productId
        """, nativeQuery = true)
    int updateDescriptionVector(@Param("productId") Long productId, @Param("vectorString") String vectorString);

    // 이메일로 조회
    List<Product> findByMember_Email(String email);

}