package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_option_id")
    private ProductOption productOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * 정적 팩토리 메서드 (수량 검증 포함)
     */
    public static CartItem create(Product product, ProductOption productOption, int quantity) {
        validatePositiveQuantity(quantity);
        validateProductOptionBelongsToProduct(product, productOption);

        return CartItem.builder()
                .product(product)
                .productOption(productOption)
                .quantity(quantity)
                .build();
    }

    /**
     * 수량 증가
     */
    public void increaseQuantity(int amount) {
        validatePositiveAmount(amount);
        this.quantity += amount;
    }

    /**
     * 수량 변경 (증가/감소)
     */
    public void updateQuantity(int delta) {
        int newQuantity = this.quantity + delta;
        validatePositiveQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    /**
     * 옵션 변경 (상품 일치 검증)
     */
    public void changeOption(ProductOption newOption) {
        if (newOption == null) {
            throw new IllegalArgumentException("상품 옵션은 null일 수 없습니다.");
        }
        validateProductOptionBelongsToProduct(this.product, newOption);
        this.productOption = newOption;
    }

    /**
     * Cart 할당 (package-private, Cart에서만 호출)
     */
    void assignCart(Cart cart) {
        this.cart = cart;
    }

    /**
     * Cart 제거 (package-private, Cart에서만 호출)
     */
    void clearCart() {
        this.cart = null;
    }

    /**
     * 동일 상품/옵션 여부 확인 (ID 기반)
     */
    public boolean isSameProductAndOptionById(Long productId, Long productOptionId) {
        return this.product.getNumber().equals(productId)
            && this.productOption.getId().equals(productOptionId);
    }

    // 검증 메서드들
    private static void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }
    }

    private void validatePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("증가량은 양수여야 합니다.");
        }
    }

    private static void validateProductOptionBelongsToProduct(Product product, ProductOption option) {
        if (!option.getProduct().getNumber().equals(product.getNumber())) {
            throw new IllegalArgumentException(
                String.format("옵션(ID: %d)은 상품(ID: %d)에 속하지 않습니다.",
                    option.getId(), product.getNumber())
            );
        }
    }
}
