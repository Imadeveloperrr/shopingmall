package com.example.crud.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;
import org.mockito.InjectMocks;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;

import java.util.List;


@ExtendWith(MockitoExtension.class)
public class RecommendationEngineTest {

//    @Mock
//    private ProductVectorService mockProductVectorService;

    @InjectMocks
    private RecommendationEngine engine;

    @Test @DisplayName("일반적인 메시지로 추천 요청")
    void getRecommendations() {
        // Given
        String message = "나이키 운동화 푹신하면서도 달리기할떄 좋은걸로 약간 외관이 날카로운 느낌을 주는 신발 추천해줘";
        int limit = 3;

        // When
        List<ProductMatch> recommendations = engine.getRecommendations(message, limit);

        // Then
        assertThat(recommendations).isNotNull();
        assertThat(recommendations).hasSize(2);

    }

    @Test @DisplayName("빈 메시지 또는 잘못된 추천 개수 요청 시 예외 발생")
    void getRecommendations_exception() {
        // Given
        String message = "";
        int limit = 0;

        // When
        assertThatThrownBy( () -> engine.getRecommendations(message, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.getRecommendations("하이", limit))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
