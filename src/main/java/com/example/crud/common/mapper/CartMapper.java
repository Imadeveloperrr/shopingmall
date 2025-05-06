package com.example.crud.common.mapper;

import com.example.crud.data.cart.dto.CartDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartMapper {
    CartDto findCartByMemberId(@Param("memberId") Long memberId);
}
