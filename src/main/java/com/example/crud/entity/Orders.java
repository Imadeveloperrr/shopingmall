package com.example.crud.entity;

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

    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Integer totalAmount; // 총 결제 금액

    @Column(nullable = false)
    private Integer deliveryFee; // 배송료

    @Column(nullable = false)
    private String deliveryMethod; // 배송 방법 (Ex : 오늘출발, 새벽도착)

    @Column(nullable = false)
    private String deliveryMemo; // 배송 메모

    @Column(nullable = false)
    private String receiverName; // 수령인 이름

    @Column(nullable = false)
    private String receiverPhone; // 수령인 전화번호

    @Column(nullable = false)
    private String receiverMobile; // 수령인 핸드폰 번호

    @Column(nullable = false)
    private String receiverAddress; // 배송 주소

    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrders(this);
    }

    public void removeOrderItem(OrderItem orderItem) {
        orderItems.remove(orderItem);
        orderItem.setOrders(null);
    }
}
