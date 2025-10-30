package com.example.crud.common.mapper;

import com.example.crud.data.cart.dto.query.CartQueryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartMapper {

    /**
     * 회원 번호로 장바구니 조회 (MyBatis 전용 Query DTO 반환).
     */
    CartQueryDto findCartByMemberId(@Param("memberId") Long memberId);
}
