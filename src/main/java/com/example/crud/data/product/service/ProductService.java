package com.example.crud.data.product.service;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;

import java.util.List;

public interface ProductService {
    List<Product> getProducts();
    List<ProductResponseDto> getProductsByMemberId(Long memberId);

    List<ProductResponseDto> getMyProducts();
}
