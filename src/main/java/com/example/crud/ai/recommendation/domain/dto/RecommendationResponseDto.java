package com.example.crud.ai.recommendation.domain.dto;

import com.example.crud.data.product.dto.ProductResponseDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecommendationResponseDto {
    private Long conversationId;
    private String aiResponse;
    private List<ProductMatch> recommendations;
    private List<ProductResponseDto> recommendedProducts;
    private Integer totalRecommendations;
}
