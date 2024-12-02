package com.example.crud.data.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOptionDto {
    private Long id;
    private String color;
    private String size;
    private Integer stock;
}