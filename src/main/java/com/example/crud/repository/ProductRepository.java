package com.example.crud.repository;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<ProductResponseDto> findByMemberId(Long memberId);
}
