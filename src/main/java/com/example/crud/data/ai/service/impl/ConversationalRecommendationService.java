package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.dto.Preference;
import com.example.crud.data.ai.dto.RecommendationResponseDto;
import com.example.crud.data.ai.service.ChatGPTIntegrationService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.ConversationMessage;
import com.example.crud.entity.UserPreference;
import com.example.crud.enums.MessageType;
import com.example.crud.repository.UserPreferenceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class ConversationalRecommendationService{

    private final ConversationService conversationService;
    private final ChatGPTIntegrationService chatGPTIntegrationService;
    private final RecommendationService recommendationService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PreferenceMergeService preferenceMergeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 사용자의 메시지를 처리하여 대화 기록을 업데이트하고,
     * 두 소스의 NLP 결과(예: ChatGPT와 Hugging Face)를 비동기로 호출하여 선호 정보를 병합한 후,
     * 이를 기반으로 추천 상품을 산출합니다.
     */
    @Transactional
    public RecommendationResponseDto processUserMessage(Long conversationId, String userMessage) {
        // 1. 사용자 메시지를 저장하고 대화 기록을 조회합니다.
        conversationService.addMessage(conversationId, MessageType.USER, userMessage);
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId);

        // 2. 두 소스의 선호 정보 추출 (예시: ChatGPT와 Hugging Face)
        CompletableFuture<String> chatGptFuture = chatGPTIntegrationService.extractUserPreferenceFromChatGPTAsync(messages, userMessage);
        CompletableFuture<String> hfFuture = chatGPTIntegrationService.extractUserPreferenceFromHFAsync(messages, userMessage);
        CompletableFuture.allOf(chatGptFuture, hfFuture).join();
        String chatGptResult = chatGptFuture.join();
        String hfResult = hfFuture.join();
        log.info("ChatGPT 결과: {}", chatGptResult);
        log.info("허깅페이스 결과: {}", hfResult);

        // 3. 두 결과를 병합하여 최종 Preference 객체를 생성하고 JSON으로 변환
        Preference mergedPreference = preferenceMergeService.mergePreferences(hfResult, chatGptResult);
        String unifiedPreferenceJson;
        try {
            unifiedPreferenceJson = objectMapper.writeValueAsString(mergedPreference);
        } catch (Exception e) {
            log.error("Preference JSON 변환 실패: {}", e.getMessage());
            throw new RuntimeException("Preference JSON 변환 실패: " + e.getMessage(), e);
        }

        // 4. UserPreference 업데이트 (회원당 1개)
        ConversationMessage firstMessage = messages.get(0);
        Long memberId = firstMessage.getConversation().getMember().getNumber();
        Optional<UserPreference> optionalPreference = userPreferenceRepository.findByMemberId(memberId);
        UserPreference userPreference;
        if (optionalPreference.isPresent()) {
            userPreference = optionalPreference.get();
            userPreference.setPreferences(unifiedPreferenceJson);
        } else {
            userPreference = UserPreference.builder()
                    .member(firstMessage.getConversation().getMember())
                    .preferences(unifiedPreferenceJson)
                    .build();
        }
        userPreferenceRepository.save(userPreference);
        log.info("UserPreference 업데이트 완료: {}", unifiedPreferenceJson);

        // 5. 대화 턴(예: 4턴 이상)이 확보되면 추천 로직 실행
        List<ProductResponseDto> recommendations = null;
        if (messages.size() >= 4) {
            recommendations = recommendationService.getPersonalizedRecommendations(unifiedPreferenceJson);
        }

        // 6. 최종 결과 반환
        RecommendationResponseDto responseDto = new RecommendationResponseDto();
        responseDto.setSystemResponse("통합된 사용자 선호 정보: " + unifiedPreferenceJson);
        responseDto.setRecommendedProducts(recommendations);
        return responseDto;
    }
}
