package com.example.crud.repository;

import com.example.crud.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    Optional<ProductOption> findByProduct_NumberAndColorAndSize(Long productId, String color, String size);
    List<ProductOption> findByProduct_Number(Long productId);
}
