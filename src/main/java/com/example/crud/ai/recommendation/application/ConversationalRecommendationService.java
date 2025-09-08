package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.recommendation.domain.dto.RecommendationResponseDto;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 대화형 추천 서비스 - 사용자 메시지를 처리하고 상품을 추천
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {
    
    private final ConversationCommandService conversationCommandService;
    private final RecommendationEngine recommendationEngine;
    
    /**
     * 사용자 메시지 처리 및 추천 생성
     */
    public RecommendationResponseDto processUserMessage(Long conversationId, String userMessage) {
        try {
            // 1. 사용자 메시지를 대화에 저장
            conversationCommandService.addMessage(conversationId, MessageType.USER, userMessage);
            log.debug("사용자 메시지 저장 완료: conversationId={}", conversationId);
            
            // 2. 추천 엔진을 통해 상품 추천 생성
            List<ProductMatch> recommendations = recommendationEngine.getRecommendations(userMessage, 10);
            log.debug("추천 생성 완료: {} 개 상품", recommendations.size());
            
            // 3. AI 응답 메시지 생성
            String aiResponse = generateAIResponse(userMessage, recommendations);
            
            // 4. AI 응답을 대화에 저장
            conversationCommandService.addMessage(conversationId, MessageType.ASSISTANT, aiResponse);
            log.debug("AI 응답 저장 완료: conversationId={}", conversationId);
            
            // 6. 응답 DTO 생성
            return RecommendationResponseDto.builder()
                    .conversationId(conversationId)
                    .aiResponse(aiResponse)
                    .recommendations(recommendations)
                    .totalRecommendations(recommendations.size())
                    .build();
                    
        } catch (Exception e) {
            log.error("대화형 추천 처리 중 오류 발생: conversationId={}, message={}", 
                     conversationId, userMessage, e);
            
            // 오류 시 기본 응답
            return RecommendationResponseDto.builder()
                    .conversationId(conversationId)
                    .aiResponse("죄송합니다. 현재 추천 서비스에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                    .recommendations(List.of())
                    .totalRecommendations(0)
                    .build();
        }
    }
    
    /**
     * AI 응답 메시지 생성
     */
    private String generateAIResponse(String userMessage, List<ProductMatch> recommendations) {
        if (recommendations.isEmpty()) {
            return "죄송합니다. 현재 요청하신 조건에 맞는 상품을 찾을 수 없습니다. " +
                   "다른 키워드나 조건으로 다시 검색해 보시겠어요?";
        }
        
        StringBuilder response = new StringBuilder();
        response.append(String.format("총 %d개의 상품을 찾았습니다! ", recommendations.size()));
        
        if (recommendations.size() <= 3) {
            response.append("추천 상품들을 확인해 보세요:");
        } else {
            response.append("특히 상위 3개 상품이 회원님의 취향에 잘 맞을 것 같아요:");
        }
        
        // 상위 3개 상품에 대한 간단한 설명 추가
        recommendations.stream()
                .limit(3)
                .forEach(product -> {
                    response.append(String.format("\n• %s (%.1f%% 일치)", 
                            product.name(), product.score() * 100));
                });
        
        response.append("\n\n더 자세한 정보나 다른 상품을 원하시면 언제든 말씀해 주세요!");
        
        return response.toString();
    }
}