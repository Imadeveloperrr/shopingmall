package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "product")
public class Product {
    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
    private Long number;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String intro;

    @Column(nullable = false)
    @Lob
    private String description;

    @Column(nullable = false)
    private String category;

    /*
        ManyToOne = 여러개의 Product가 하나의 Member에 연관될수 있음을 나타냄 다대일 관계
        FetchType.Lazy = 연관된 엔티티를 실제로 사용할 때까지 로드하지 않는 전략.
        예를들어, getMemerName(), getMemberNickname 메서드가 호출할 때 Member 엔티티가 로드됨.
        JoinColumn = member_id 라는 외래키를 Product 테이블에 추가.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    public String getMemberEmail() {
        return member.getEmail();
    }

    public String getMemberName() {
        return member.getName();
    }

    public String getMemberNickname() {
        return member.getNickname();
    }
}
