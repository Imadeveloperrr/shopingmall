package com.example.crud.data.product.service;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService {
    List<ProductResponseDto> getProducts();
    List<ProductResponseDto> getProductsByMemberId(Long memberId);

    List<ProductResponseDto> getMyProducts();

    ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile file);
    ProductResponseDto getUpdateProduct(ProductDto productDto, MultipartFile file);
    void getDeleteProduct(Long id);
    ProductResponseDto getProductById(Long id);
    ProductResponseDto getProductByName(String name);

    List<ProductOptionDto> getProductOptions(Long productId);
}
