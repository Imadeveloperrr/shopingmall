package com.example.crud.data.product.service;

import com.example.crud.data.product.exception.ProductNotFoundException;
import com.example.crud.data.product.exception.ProductOptionNotFoundException;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import com.example.crud.repository.ProductOptionRepository;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product 조회 전용 서비스
 * - 단일 책임 원칙에 따라 조회만 담당
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFindService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    /**
     * 상품 조회
     */
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /**
     * 상품 옵션 조회
     */
    public ProductOption getProductOption(Long productId, String color, String size) {
        return productOptionRepository.findByProduct_NumberAndColorAndSize(productId, color, size)
                .orElseThrow(() -> new ProductOptionNotFoundException(productId, color, size));
    }
}
