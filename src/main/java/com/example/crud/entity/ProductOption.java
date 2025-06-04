package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_option",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id", "color", "size"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String color;

    @Column(nullable = false, length = 20)
    private String size;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;
}