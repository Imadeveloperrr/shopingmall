package com.example.crud.common.mapper;

import com.example.crud.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> findAllProducts();
    List<Product> findProductsByMemberId(@Param("memberId") Long memberId);
    Product findProductByNumber(@Param("number") Long number);

}
