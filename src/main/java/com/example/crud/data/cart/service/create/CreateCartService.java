package com.example.crud.data.cart.service.create;

import com.example.crud.entity.Cart;
import com.example.crud.entity.Member;
import com.example.crud.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/**
 * 장바구니 생성 서비스 (인터페이스 없음)
 * - 내부 헬퍼
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreateCartService {

    private final CartRepository cartRepository;

    /**
     * 장바구니 조회 또는 생성
     * - save는 여기서만 호출 (책임 통일)
     */
    @Transactional
    public Cart getOrCreateCart(Long memberId) {
        return cartRepository.findByMemberNumber(memberId)
                .orElseGet(() -> {
                    Cart cart = Cart.builder()
                            .member(Member.builder().number(memberId).build())
                            .cartItems(new ArrayList<>())
                            .build();
                    return cartRepository.save(cart);  // 생성 시에만 명시적 save
                });
    }
}
