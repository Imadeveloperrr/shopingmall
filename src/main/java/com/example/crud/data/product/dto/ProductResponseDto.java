package com.example.crud.data.product.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponseDto {
    private String name;
    private int price;
    private int number;
    private String brandName;
    private String imgUrl;
    private String description;
    private String categoryName;
}
