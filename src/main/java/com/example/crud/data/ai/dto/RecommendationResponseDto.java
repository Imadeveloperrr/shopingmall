package com.example.crud.data.ai.dto;

import com.example.crud.data.product.dto.ProductResponseDto;
import lombok.Data;

import java.util.List;

@Data
public class RecommendationResponseDto {
    private String systemResponse;
    private List<ProductResponseDto> recommendedProducts;
}
