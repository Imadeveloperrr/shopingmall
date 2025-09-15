package com.example.crud.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import com.example.crud.ai.recommendation.application.RecommendationEngine;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;

import java.util.List;


public class RecommendationEngineTest {

    @Test @DisplayName("빈 메시지 또는 잘못된 추천 개수 요청 시 예외 발생")
    void getRecommendations_exception() {
        // Given
        RecommendationEngine engine = new RecommendationEngine(null); // Mock 없이 단순 검증
        String emptyMessage = "";
        int invalidLimit = 0;

        // When & Then
        assertThatThrownBy(() -> engine.getRecommendations(emptyMessage, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("추천 생성할 메시지가 없습니다");
                
        assertThatThrownBy(() -> engine.getRecommendations("하이", invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("추천 개수는 1~10 사이여야 합니다");
    }
}
