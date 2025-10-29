package com.example.crud.data.cart.service.internal;

import com.example.crud.data.cart.exception.CartItemNotFoundException;
import com.example.crud.data.cart.exception.CartNotFoundException;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import com.example.crud.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 내부 조회 서비스 (인터페이스 없음)
 * - 서비스 간 공용 조회 로직
 * - JPA 더티체킹 활용, 별도 save 불필요
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartInternalService {

    private final CartRepository cartRepository;

    /**
     * 장바구니 조회 (없으면 예외)
     */
    public Cart getCart(Long memberId) {
        return cartRepository.findByMemberNumber(memberId)
                .orElseThrow(() -> new CartNotFoundException(memberId));
    }

    /**
     * 장바구니에서 아이템 찾기
     */
    public CartItem getCartItemFromCart(Cart cart, Long cartItemId) {
        return cart.getCartItems().stream()
                .filter(item -> item.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId));
    }
}
