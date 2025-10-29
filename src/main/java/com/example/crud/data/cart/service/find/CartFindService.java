package com.example.crud.data.cart.service.find;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.response.CartResponse;
import com.example.crud.entity.CartItem;

import java.util.List;

/**
 * 장바구니 조회 서비스
 * - 인터페이스 유지 이유: MyBatis/JPA 혼용, 캐시/관리자 뷰 확장 가능성
 */
public interface CartFindService {

    /**
     * 인증된 회원의 장바구니 조회 (MyBatis)
     */
    CartDto getCartByAuthenticateMember();

    /**
     * 선택된 아이템만 조회
     */
    CartResponse getSelectedItems(List<Long> cartItemIds);

    /**
     * 단일 아이템 조회
     */
    CartItem getCartItem(Long cartItemId);
}
