package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Orders orders;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_size_id")
    private ProductSize productSizeEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;  // 상품 정보 직접 참조 추가

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer price;  // 주문 당시의 가격

    @Column(nullable = false)
    private String productName;  // 주문 당시의 상품명

    @Column(nullable = false)
    private String productSize;  // 주문 당시의 사이즈

    // 취소 시 재고 원복을 위한 메서드
    public void cancel() {
        ProductSize size = this.getProductSizeEntity();
        size.setStock(size.getStock() + this.quantity);
    }

    // 주문 상품 금액 계산
    public int calculateAmount() {
        return price * quantity;
    }
}