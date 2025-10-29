package com.example.crud.data.cart.service.clear;

import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.service.internal.CartInternalService;
import com.example.crud.entity.Cart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 비우기 서비스
 * - 인터페이스 없음 (단일 구현만 필요)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClearCartService {

    private final SecurityUtil securityUtil;
    private final CartInternalService cartInternalService;

    @Transactional
    public void clearCart() {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);
        cart.clearItems();
        // JPA 더티체킹으로 자동 저장
    }
}
