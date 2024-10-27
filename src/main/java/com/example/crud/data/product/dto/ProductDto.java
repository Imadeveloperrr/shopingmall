package com.example.crud.data.product.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {
    private Long number;
    private String name;
    private int price;
    private String brand;
    private String intro;
    private String color;
    private String description;
    private String category;

    private List<ProductSizeDto> productSizes;
}
