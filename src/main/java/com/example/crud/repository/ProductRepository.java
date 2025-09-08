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

    /**
     * 카테고리 기반으로 상품 목록을 조회합니다.
     * 실제 업무에서는 Category Enum을 사용하는 등 타입에 맞게 조정이 필요합니다.
     *
     * @param category 상품 카테고리
     * @return 상품 목록
     */
    List<Product> findByCategory(Category category);

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

    // 카테고리 목록에 속하고 벡터가 있는 상품 조회
    @Query("SELECT p FROM Product p WHERE p.category IN :categories AND p.descriptionVector IS NOT NULL")
    List<Product> findByCategoryInAndDescriptionVectorIsNotNull(@Param("categories") List<String> categories);

    // 최신 상품 20개 조회 (ID 내림차순)
    List<Product> findTop20ByOrderByNumberDesc();

    // 벡터가 있는 최신 상품 20개 조회
    List<Product> findTop20ByDescriptionVectorIsNotNullOrderByNumberDesc();

    // ID 기준 내림차순 정렬 (Product 엔티티의 PK는 number 필드)
    @Query("SELECT p FROM Product p ORDER BY p.number DESC")
    List<Product> findTop20ByOrderByIdDesc(org.springframework.data.domain.Pageable pageable);

    // 이메일로 조회
    List<Product> findByMember_Email(String email);

}