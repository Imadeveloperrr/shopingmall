package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private int amount;
    private String paymentMethod;
    private String transactionId; // 결제 완료 후 PG사로부터 받은 거래 ID
    private String status;          // SUCCESS, FAILED 등 결제 상태
    private LocalDateTime createdAt;
}
