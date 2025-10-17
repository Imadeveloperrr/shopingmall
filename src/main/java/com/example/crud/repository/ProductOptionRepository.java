package com.example.crud.repository;

import com.example.crud.entity.ProductOption;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 *  - findBy → SELECT 쿼리
 *  - Product_Number → product.number (연관 객체의 필드)
 *  - And → AND 조건
 *  - Color → color 필드
 *  - And → AND 조건
 *  - Size → size 필드
 */
@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    Optional<ProductOption> findByProduct_NumberAndColorAndSize(
            Long productId,
            String color,
            String size
    );

    List<ProductOption> findByProduct_Number(Long productId);

    /**
     * 재고 차감 (원자적 업데이트)
     *
     * @param id       상품 옵션 ID
     * @param quantity 차감할 수량
     * @return 업데이트된 row 수 (0이면 재고 부족 또는 존재하지 않음)
     */
    @Modifying
    @Query(value = "UPDATE product_option SET stock = stock - :quantity " +
                    "WHERE id = :id AND stock >= :quantity", nativeQuery = true)
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 재고 복구 (주문 취소 시)
     *
     * @param id       상품 옵션 ID
     * @param quantity 복구할 수량
     * @return 업데이트된 row 수.
     */
    @Modifying
    @Query(value = "UPDATE product_option SET stock = stock + :quantity " +
                    "WHERE id = :id", nativeQuery = true)
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
