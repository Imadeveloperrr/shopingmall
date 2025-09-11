package com.example.crud.entity;

import com.example.crud.enums.Category;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(nullable = false)
    private String intro;

    @Column(nullable = false, columnDefinition="TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(name = "sub_category")
    private String subCategory;

    /*
        ManyToOne = 여러개의 Product가 하나의 Member에 연관될수 있음을 나타냄 다대일 관계
        FetchType.Lazy = 연관된 엔티티를 실제로 사용할 때까지 로드하지 않는 전략.
        예를들어, getMemerName(), getMemberNickname 메서드가 호출할 때 Member 엔티티가 로드됨.
        JoinColumn = member_id 라는 외래키를 Product 테이블에 추가.
    */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductOption> productOptions = new ArrayList<>();

    // 양방향 관계 관리 메서드 추가
    public void addProductOption(ProductOption option) {
        productOptions.add(option);
        option.setProduct(this);
    }


    // 상품 설명 임베딩 (1536차원 - text-embedding-3-small 모델) - pgvector
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "description_vector",
    columnDefinition = "vector(1536)", nullable = true)
    private float[] descriptionVector;

    public String getMemberEmail() {
        return member.getEmail();
    }

}
