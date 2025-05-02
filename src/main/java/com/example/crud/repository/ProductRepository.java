package com.example.crud.repository;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.enums.Category;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
