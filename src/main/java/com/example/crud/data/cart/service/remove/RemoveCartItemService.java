package com.example.crud.data.cart.service.remove;

import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.service.internal.CartInternalService;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 장바구니 아이템 제거 서비스
 * - 인터페이스 없음 (단일 구현만 필요)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RemoveCartItemService {

    private final SecurityUtil securityUtil;
    private final CartInternalService cartInternalService;

    @Transactional
    public void removeCartItem(Long cartItemId) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);

        CartItem cartItem = cartInternalService.getCartItemFromCart(cart, cartItemId);
        cart.removeItem(cartItem);
        // JPA 더티체킹으로 자동 저장
    }

    @Transactional
    public void removeOrderedItems(List<Long> cartItemIds) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);

        Set<Long> itemIdsToRemove = new HashSet<>(cartItemIds);

        List<CartItem> itemsToRemove = cart.getCartItems().stream()
                .filter(item -> itemIdsToRemove.contains(item.getId()))
                .collect(Collectors.toList());  // JDK 8+ 호환

        itemsToRemove.forEach(cart::removeItem);
        // JPA 더티체킹으로 자동 저장
    }
}
