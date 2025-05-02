package com.example.crud.data.product.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponseDto {
    private Long number;
    private String name;
    private String price;  // 원화 형식으로 반환 ("1,000원" 형태)
    private String brand;
    private String intro;
    private String imageUrl;
    private String description;
    private String category;
    private String subCategory;
    private boolean permission;
    private List<ProductOptionDto> productOptions;

    private Double relevance;   // 유사도 점수(0.0~1.0)

    public void setDescription(String description) {
        this.description = description != null ? description.replace("\n", "<br>") : "";
    }
}