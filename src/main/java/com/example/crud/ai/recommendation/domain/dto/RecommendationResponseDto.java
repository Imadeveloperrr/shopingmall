package com.example.crud.ai.recommendation.domain.dto;

import com.example.crud.data.product.dto.ProductResponseDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationResponseDto {
    private String systemResponse;
    private List<ProductResponseDto> recommendedProducts;
}
