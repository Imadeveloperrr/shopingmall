package com.example.crud.entity;

import com.example.crud.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "orders")
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Builder.Default
    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime orderDate;

    // 주문 상태를 ENUM으로 관리
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Integer totalAmount;

    @Column(nullable = false)
    private Integer deliveryFee;

    @Column(nullable = false)
    private String deliveryMethod;

    @Column
    private String deliveryMemo; // 배송메모는 필수가 아닐 수 있음

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String receiverPhone;

    @Column(nullable = false)
    private String receiverMobile;

    @Column(nullable = false)
    private String receiverAddress;

    // 배송 추적 관련 필드 추가
    @Column
    private String trackingNumber;

    @Column
    private LocalDateTime deliveryStartDate;

    @Column
    private LocalDateTime deliveryEndDate;

    // 결제 관련 필드 추가
    @Column(nullable = false)
    private String paymentMethod;  // 결제 방법 (카드, 무통장 등)

    @Column
    private String paymentStatus;  // 결제 상태

    @Column
    private LocalDateTime paidAt;  // 결제 완료 시간

    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrders(this);
    }

    public void removeOrderItem(OrderItem orderItem) {
        orderItems.remove(orderItem);
        orderItem.setOrders(null);
    }

    // 주문 취소 메서드
    public void cancel() {
        if (this.status != OrderStatus.ORDER_PLACED) {
            throw new IllegalStateException("이미 배송이 시작된 주문은 취소할 수 없습니다.");
        }
        this.status = OrderStatus.CANCELLED;

        // 재고 원복
        for (OrderItem orderItem : orderItems) {
            orderItem.cancel();
        }
    }

    // 총 주문 금액 계산 메서드
    public int calculateTotalAmount() {
        return orderItems.stream()
                .mapToInt(OrderItem::calculateAmount)
                .sum();
    }
}

