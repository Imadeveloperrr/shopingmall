package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.config.ChatGptProperties;
import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.conversation.application.query.ConversationQueryService;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.recommendation.domain.dto.*;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Member;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {

    private final ConversationCommandService cmdSvc;
    private final ConversationQueryService qrySvc;
    private final ConversationRepository convRepo;
    private final ChatGptServiceLite gptSvc;
    private final RecommendationService recSvc;
    private final UserPreferenceRepository prefRepo;
    private final ChatGptProperties prop;

    @Transactional
    public RecommendationResponseDto processUserMessage(Long convId, String userMsg) {

        /* 1. 사용자 메시지 저장 */
        cmdSvc.addMessage(convId, MessageType.USER, userMsg);

        // 2. 대화 정보 및 회원 정보 조회.
        Conversation conversation = convRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));
        Member member = conversation.getMember();

        // 3. 대화 히스토리 조회
        List<MessageDto> messageSlice = qrySvc.fetchMessages(convId, null, 0, 100).getContent();
        List<ChatMessage> chatMessages = messageSlice.stream().map(m -> new ChatMessage(m.getRole(), m.getContent())).toList();

        // 4. ChatGPT로 세밀 의도 JSON 추출
        String prompt = """
                사용자 메시지: "%s"
                
                사용자의 의도를 분석하여 상품 추천에 필요한 정보를 추출해주세요.
                다음 JSON 형식으로 반환해주세요.
                {
                    "category": "상의/하의/아우터/원피스/가방/신발/악세사리 중 하나",
                    "style": "캐주얼/포멀/스포티/빈티지 등",
                    "color": "색상",
                    "season": "봄/여름/가을/겨울",
                    "priceRange": "저가/중가/고가",
                    "keywords": ["추가 키워드들"]
                }
                
                사용자가 언급하지 않은 항목은 null로 표시하세요.
                """.formatted(userMsg);

        String intentJson = gptSvc.completion(chatMessages, prompt).block(Duration.ofSeconds(prop.timeoutSec()));

        log.debug("[GPT] 추출된 의도: {}", intentJson);

        // 5. 사용자 선호도 업데이트
        updateUserPreference(member, intentJson, userMsg);

        // 6. 추천 서비스 호출
        List<ProductResponseDto> products = recSvc.recommend(userMsg);

        // 7. AI 어시스턴트 응답 생성
        String assistantResponse = generateAssistantResponse(intentJson, products);

        // 8. 어시스턴트 응답 저장
        cmdSvc.addMessage(convId, MessageType.ASSISTANT, assistantResponse);

        // 9. 결과 반환
        return RecommendationResponseDto.builder()
                .systemResponse(assistantResponse)
                .recommendedProducts(products)
                .build();
    }

    // 사용자 선호도 업데이트
    private void updateUserPreference(Member member, String intentJson, String originalMessage) {
        try {
            // 기존 선호도 조회 또는 신규 생성
            UserPreference preference = prefRepo.findByMember_Number(member.getNumber())
                    .orElseGet(() -> UserPreference.builder()
                            .member(member)
                            .preferences("{}")
                            .build());

            // 기존 선호도와 새로운 의도를 병합
            String mergedPreferences = mergePreferences(preference.getPreferences(), intentJson);

            preference.setPreferences(mergedPreferences);
            preference.setLastUpdated(LocalDateTime.now());

            prefRepo.save(preference);
            log.info("사용자 {} 선호도 업데이트 완료", member.getNumber());
        } catch (Exception e) {
            log.error("선호도 업데이트 실패: {}", e.getMessage(), e);
        }
    }

    // 기존 선호도와 새로운 의도 병합
    private String mergePreferences(String existingPrefs, String newIntent) {
        try {
            // 간단한 병합 로직 - 실제로는 더 정교환 병합이 필요.
            // 예: 가중치 적용, 시간 기반 감쇠 등
            return newIntent; // 임시로 새로운 의도로 대체.
        } catch (Exception e) {
            return newIntent;
        }
    }

    // AI 어시스턴트 응답 생성
    private String generateAssistantResponse(String intentJson, List<ProductResponseDto> products) {
        if (products.isEmpty()) {
            return "죄송합니다. 말씀하신 조건에 맞는 상품을 찾지 못했습니다. 다른 조건으로 검색해보시겠어요?";
        }

        StringBuilder response = new StringBuilder();
        response.append("고객님의 취향을 분석하여 ");
        response.append(products.size()).append("개의 상품을 추천해드립니다.\n\n");

        // 상위 3개 상품에 대한 간단한 설명
        int count = Math.min(3, products.size());
        for (int i=0; i<count; i++) {
            ProductResponseDto product = products.get(i);
            response.append(i + 1).append(". ")
                    .append(product.getBrand()).append(" ")
                    .append(product.getName())
                    .append(" (").append(product.getPrice()).append("원)\n");
        }

        if (products.size() > 3) {
            response.append("\n더 많은 상품이 준비되어 있습니다!");
        }

        return response.toString();
    }
}