package com.example.crud.data.product.service;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProductService {
    List<ProductResponseDto> getProducts();
    List<ProductResponseDto> getProductsByMemberId(Long memberId);

    List<ProductResponseDto> getMyProducts();

    ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile file) throws IOException;
    ProductResponseDto getUpdateProduct(ProductDto productDto);
    ProductResponseDto getDeleteProduct(ProductDto productDto);
    ProductResponseDto getProductById(Long id);
    ProductResponseDto getProductByName(String name);

}
