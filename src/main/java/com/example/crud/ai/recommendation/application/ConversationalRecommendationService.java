package com.example.crud.ai.recommendation.application;

import java.util.*;

import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.recommendation.domain.dto.RecommendationResponseDto;
import com.example.crud.ai.recommendation.domain.dto.UserMessageRequestDto;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
/*
 응답 메시지도 저장하는 이유.

 무엇을 노출했는지 재구성 가능
AI 응답은 당시 노출된 상품 ID와 노출 맥락의 원본이다. 이걸 남겨야 나중에 클릭, 장바구니, 구매 같은 유저 행동과 정확히 매칭해 “무엇을 보여줬더니 무엇을 했는가”를 계산할 수 있다.
선호 신호를 누적해 가중치 업데이트
노출 목록(productIds)에 대한 클릭, 장바구니, 구매 이벤트를 합치면 사용자별 속성 선호를 점진적으로 학습할 수 있다. 예시
1) 카테고리, 브랜드, 가격대, 색상 등의 속성별 가중치 업데이트
2) 최근성 가중치 적용으로 최신 취향을 더 반영
3) 이미 여러 번 노출했는데 반응 없는 상품, 속성에는 페널티 부여
4) 다음 턴 추천 시 동일 상품 중복 노출 회피와 유사 상품 가중치 상향
대화 맥락 반영
대화 흐름에서 “가벼운 러닝화”, “10만원 이하” 같은 의도가 바뀌면, 직전 턴에 실제로 어떤 후보를 보여줬는지와 함께 맥락 변화를 기록해 다음 턴 후보 생성과 랭킹에 반영할 수 있다.
지표의 의미와 활용


CTR(Click Through Rate)
공식: 클릭 수 / 노출 수
노출 수는 AI 응답에 포함된 상품 개수, 클릭은 그 중 어떤 상품을 눌렀는지로 집계한다. 템플릿, 모델, 카테고리, 사용자 세그먼트별로 비교 가능.
CVR(Conversion Rate)
공식: 구매 수 / 노출 수 또는 구매 수 / 클릭 수
구매는 보통 1일 등 어트리뷰션 윈도우 내 매칭한다. 고가 상품은 클릭당 매출(RPU, RPI) 같이 수익 지표가 더 적합할 수 있다.
A/B 테스트
사용자 또는 대화 단위로 실험군 배정(랜덤 키). 서로 다른 모델 버전, 템플릿, 점수 가중치를 노출하고 CTR, CVR, 매출, 세션 길이 같은 지표를 비교한다. 샘플 수와 통계적 유의성 검정으로 결론을 낸다.
모델 버전별 품질 비교
AI 응답에 modelVersion을 태깅해 두면, 버전별 CTR, CVR, 매출을 같은 기간, 같은 트래픽 조건에서 공정하게 비교할 수 있다.
상담사 핸드오버의 의미


AI가 해결하지 못해 사람이 이어받는 상황에서, 상담사는 직전까지의 대화와 AI가 무엇을 보여줬는지를 즉시 확인해야 한다.
응답 원문과 노출 상품 로그가 있으면, 상담사가 중복 제안 없이 맥락을 이어서 정확한 대안을 제시할 수 있다.
품질 사고나 CS 이슈가 났을 때 “당시 어떤 답과 추천이 나갔는지”를 그대로 재현 가능하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {

    private final RecommendationEngine recommendationEngine;
    private final ConversationCommandService commandService;

    public RecommendationResponseDto processUserMessage(Long id, String message) {
        try {
            // ★ 유저 매시지 저장과, AI 응답 메시지 저장이 서로 동기임. 비동기로 최적화 가능.


            // 유저 대화 저장
            commandService.addMessage(id, MessageType.USER, message);

            // 상품 추천.
            List<ProductMatch> recommendations = recommendationEngine.getRecommendations(message, 5);

            // AI 응답 메시지
            String AiResponse = generateAIResponse(recommendations);

            // 응답 메시지도 저장.
            commandService.addMessage(id, MessageType.ASSISTANT, AiResponse);

            return RecommendationResponseDto.builder()
                    .conversationId(id)
                    .aiResponse(AiResponse)
                    .recommendations(recommendations)
                    .totalRecommendations(recommendations.size())
                    .build();

        } catch (Exception e) {
            log.error("대화형 추천 처리 실패: conversationId={}, message={}, exception={}", id, message, e.getMessage());
            return RecommendationResponseDto.builder()
                    .conversationId(id)
                    .aiResponse("죄송합니다. 추천을 생성하는 중에 문제가 발생했습니다.")
                    .recommendations(List.of())
                    .totalRecommendations(0)
                    .build();
        }
    }

    public String generateAIResponse(List<ProductMatch> recommendations) {
        if (recommendations.isEmpty()) {
            return "죄송합니다. 현재 조건에 맞는 상품을 찾을 수 없습니다. 다른 검색어를 시도해 주세요.";
        }

        StringBuilder sb = new StringBuilder();
        int productCount = recommendations.size();

        // 상품 개수에 따른 맞춤형 메시지
        if (productCount == 1) {
            sb.append("검색 조건에 맞는 상품을 찾았습니다!");
        } else {
            sb.append(String.format("총 %d개의 상품을 찾았습니다.", productCount));
        }

        // 상품 소개 문구 개선
        if (productCount == 1) {
            sb.append(" 추천 상품을 소개해드릴게요:\n");
        } else if (productCount <= 3) {
            sb.append(" 모든 상품을 소개해드릴게요:\n");
        } else {
            sb.append(" 상위 3개 상품을 소개해드릴게요:\n");
        }

        // 상품 목록 표시 (최대 3개)
        recommendations.stream()
                .limit(3)
                .forEach(product -> {
                    double percentage = product.score() * 100;
                    if (percentage >= 50.0) {
                        sb.append(String.format("\n• %s (%.1f%% 일치 - 높은 관련성)",
                                product.name(), percentage));
                    } else if (percentage >= 20.0) {
                        sb.append(String.format("\n• %s (%.1f%% 일치)",
                                product.name(), percentage));
                    } else {
                        sb.append(String.format("\n• %s (%.1f%% 일치 - 참고용)",
                                product.name(), percentage));
                    }
                });

        // 맞춤형 마무리 메시지
        if (productCount == 1) {
            sb.append("\n\n이 상품에 대해 더 자세히 알고 싶으시거나 다른 검색어로 찾아보고 싶으시면 언제든 말씀해 주세요!");
        } else {
            sb.append(String.format("\n\n%s더 자세한 정보나 다른 상품을 원하시면 언제든 말씀해 주세요!",
                    productCount > 3 ? "다른 상품들도 있습니다. " : ""));
        }

        return sb.toString();
    }
}