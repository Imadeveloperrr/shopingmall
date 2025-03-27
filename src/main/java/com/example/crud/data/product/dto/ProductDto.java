package com.example.crud.data.product.dto;

import com.example.crud.enums.Category;
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
    private String description;
    private Category category;
    private String subCategory;
    private String imageUrl;

    private List<ProductOptionDto> productOptions;
}
