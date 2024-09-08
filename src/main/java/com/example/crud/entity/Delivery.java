package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "delivery")
public class Delivery { // 배송 정보 관리용

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orders_id")
    private Orders orders;

    @Column(nullable = false)
    private String receiverName; // 수령인 이름

    @Column(nullable = false)
    private String receiverPhone; // 수령인 전화번호

    @Column(nullable = false)
    private String receiverMobile; // 수령인 핸드폰 번호

    @Column(nullable = false)
    private String receiverAddress; // 수령인 주소

    @Column(nullable = false)
    private String deliveryMemo; // 배송 메모

    @Column(nullable = false)
    private String deliveryMethod; // 배송 방법 (Ex : 오늘출발, 새벽도착)

}
