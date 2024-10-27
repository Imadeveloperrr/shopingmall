package com.example.crud.data.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSizeDto {
    private String size;
    private Integer stock;
}
