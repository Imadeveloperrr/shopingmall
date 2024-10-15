package com.example.crud.mapper;

import com.example.crud.data.product.dto.ProductResponseDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<ProductResponseDto> findAllProducts();
    List<ProductResponseDto> findProductsByMemberId(@Param("memberId") Long memberId);
    ProductResponseDto findProductById(@Param("id") Long id);

}
