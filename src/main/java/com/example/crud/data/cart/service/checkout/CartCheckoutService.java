package com.example.crud.data.cart.service.checkout;

import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.cart.service.find.CartFindService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart → Order 변환용 파사드.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartCheckoutService {

    private final CartFindService cartFindService;

    /**
     * 장바구니 아이템을 주문 준비용 DTO로 변환.
     */
    public List<CartCheckoutItem> prepareCheckoutItems(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품을 선택해주세요.");
        }

        List<CartCheckoutItem> items = cartFindService.getCheckoutItems(cartItemIds);

        if (items.isEmpty()) {
            throw new IllegalArgumentException("선택한 상품을 찾을 수 없습니다.");
        }

        return items;
    }

    /**
     * 주문 아이템 총 금액 계산.
     */
    public int calculateTotalAmount(List<CartCheckoutItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream()
            .mapToInt(CartCheckoutItem::totalPrice)
            .sum();
    }
}
