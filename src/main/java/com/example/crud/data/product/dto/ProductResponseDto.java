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
    private Long number;
    private String brand;
    private String imageUrl;
    private String description;
    private String category;
}
