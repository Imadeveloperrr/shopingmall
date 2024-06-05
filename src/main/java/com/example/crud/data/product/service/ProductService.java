package com.example.crud.data.product.service;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;

import java.util.List;

public interface ProductService {
    List<Product> getProducts();
    List<ProductResponseDto> getProductsByMemberId(Long memberId);

    List<ProductResponseDto> getMyProducts();

    ProductResponseDto getAddProduct(ProductDto productDto);
    ProductResponseDto getUpdateProduct(ProductDto productDto);
    ProductResponseDto getDeleteProduct(ProductDto productDto);
    ProductResponseDto getProductById(Long id);
    ProductResponseDto getProductByName(String name);

}
