package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.dto.RecommendationResponseDto;
import com.example.crud.entity.ConversationMessage;
import com.example.crud.entity.UserPreference;
import com.example.crud.enums.MessageType;
import com.example.crud.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationalRecommendationService {

    private final ConversationService conversationService;
    private final ChatGPTIntegrationService chatGPTIntegrationService;
    private final RecommendationService recommendationService;
    private final UserPreferenceRepository userPreferenceRepository;

    /**
     * 사용자의 메시지를 처리하여 대화 기록을 업데이트하고,
     * ChatGPT를 활용해 사용자 메시지에서 상세 의도 정보를 추출한 후,
     * 해당 정보를 기반으로 고정밀 상품 추천을 수행합니다.
     *
     * 1. 사용자 메시지 저장 및 대화 기록 조회
     * 2. ChatGPT를 통해 사용자 메시지 의도 분석 (상세 요구사항 JSON 형식)
     * 3. 사용자 선호 정보(UserPreference) 업데이트
     * 4. 고정밀 추천 로직 실행
     *
     * @param conversationId 대화 ID
     * @param userMessage 사용자 메시지
     * @return RecommendationResponseDto 추천 결과 DTO
     */
    @Transactional
    public RecommendationResponseDto processUserMessage(Long conversationId, String userMessage) {
        // 1. 사용자 메시지를 저장하고 대화 기록을 조회합니다.
        conversationService.addMessage(conversationId, MessageType.USER, userMessage);
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId);

        // 2. ChatGPT를 활용하여 사용자 메시지에서 상세 의도 정보를 추출합니다.
        String prompt = "사용자 메시지: " + userMessage + "\n"
                + "상품 추천을 위한 사용자의 상세 요구사항을 아래 JSON 형식으로 추출해줘.\n"
                + "예시: { \"category\": \"\", \"style\": \"\", \"color\": \"\", \"size\": \"\" }";
        CompletableFuture<String> chatGptFuture = chatGPTIntegrationService.extractUserPreferenceFromChatGPTAsync(messages, prompt);
        String chatGptResult = chatGptFuture.join();
        log.info("ChatGPT로 추출된 사용자 상세 의도: {}", chatGptResult);

        // 3. 사용자 선호 정보(UserPreference) 업데이트 (회원 당 1개)
        ConversationMessage firstMessage = messages.get(0);
        Long memberId = firstMessage.getConversation().getMember().getNumber();
        UserPreference userPreference = userPreferenceRepository.findByMemberId(memberId)
                .orElseGet(() -> UserPreference.builder()
                        .member(firstMessage.getConversation().getMember())
                        .build());
        userPreference.setPreferences(chatGptResult);
        userPreferenceRepository.save(userPreference);
        log.info("UserPreference 업데이트 완료: {}", chatGptResult);

        // 4. 고정밀 추천 로직 실행: ChatGPT에서 추출한 상세 사용자 의도를 기반으로 추천 상품 조회
        List<ProductResponseDto> recommendations = recommendationService.getHighPrecisionRecommendations(chatGptResult);

        // 5. 추천 결과 DTO 구성 및 반환
        RecommendationResponseDto responseDto = new RecommendationResponseDto();
        responseDto.setSystemResponse("추출된 사용자 상세 의도: " + chatGptResult);
        responseDto.setRecommendedProducts(recommendations);
        return responseDto;
    }
}
