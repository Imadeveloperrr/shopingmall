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
    private String name;
    private String price;
    private Long number;
    private String brand;
    private String intro;
    private String color;
    private String imageUrl;
    private String description;
    private String category;
    private boolean permission;
    private List<ProductSizeDto> productSizes;

    public void setDescription(String description) {
        this.description = description.replace("\n", "<br>");
    }
}
