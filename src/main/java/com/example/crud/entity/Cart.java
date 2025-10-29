package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cart")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", unique = true)
    private Member member;

    @Builder.Default
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<>();

    /**
     * 장바구니에 아이템 추가
     * - 동일 상품/옵션이면 수량 증가
     * - 신규 상품이면 리스트에 추가
     */
    public void addItem(CartItem cartItem) {
        findExistingItemByIds(cartItem.getProduct().getNumber(), cartItem.getProductOption().getId())
            .ifPresentOrElse(
                existing -> existing.increaseQuantity(cartItem.getQuantity()),
                () -> {
                    cartItems.add(cartItem);
                    cartItem.assignCart(this);
                }
            );
    }

    /**
     * 장바구니 아이템 제거
     */
    public void removeItem(CartItem cartItem) {
        cartItems.remove(cartItem);
        cartItem.clearCart();
    }

    /**
     * 동일 상품/옵션 찾기 (ID 기반 - JPA 프록시 안전)
     */
    private Optional<CartItem> findExistingItemByIds(Long productId, Long productOptionId) {
        return cartItems.stream()
            .filter(item -> item.isSameProductAndOptionById(productId, productOptionId))
            .findFirst();
    }

    /**
     * 특정 CartItem이 이 장바구니에 속하는지 확인
     */
    public boolean containsItem(Long cartItemId) {
        return cartItems.stream()
            .anyMatch(item -> item.getId().equals(cartItemId));
    }

    /**
     * 장바구니 비우기
     */
    public void clearItems() {
        cartItems.forEach(CartItem::clearCart);
        cartItems.clear();
    }
}
