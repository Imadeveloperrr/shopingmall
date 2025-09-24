package com.example.crud.repository;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.enums.Category;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

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
        ORDER BY p.description_vector <#> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Product> findSimilarProductsByCategory(
            @Param("category") String category,
            @Param("queryVector") String queryVector,
            @Param("limit") int limit
    );

    // 벡터 유사도 검색을 위한 네이티브 쿼리 (코사인 유사도)
    @Query(value = """
        SELECT
            p.number as productId,
            p.name as productName,
            p.description as description,
            (1 - (p.description_vector <=> CAST(:queryVector AS vector))) as similarity
        FROM product p
        WHERE p.description_vector IS NOT NULL
        AND (1 - (p.description_vector <=> CAST(:queryVector AS vector))) > :threshold
        ORDER BY p.description_vector <=> CAST(:queryVector AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findSimilarProductsByVector(
            @Param("queryVector") String queryVector,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    // 이메일로 조회
    List<Product> findByMember_Email(String email);

}