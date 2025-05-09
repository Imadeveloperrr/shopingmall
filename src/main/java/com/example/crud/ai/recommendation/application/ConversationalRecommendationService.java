package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.config.ChatGptProperties;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.conversation.application.query.ConversationQueryService;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.recommendation.domain.dto.*;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {

    private final ConversationCommandService cmdSvc;
    private final ConversationQueryService qrySvc;
    private final ChatGptServiceLite gptSvc;
    private final EmbeddingClient embedCli;
    private final RecommendationService recSvc;
    private final UserPreferenceRepository prefRepo;
    // private final ProductQueryService productQry;
    private final ChatGptProperties prop;

    @Transactional
    public RecommendationResponseDto processUserMessage(Long convId, String userMsg) {

        /* 1️⃣ 사용자 메시지 저장 */
        cmdSvc.addMessage(convId, MessageType.USER, userMsg);

        /* 2️⃣ 대화 히스토리 조회 (fetchMessages 사용) */
        List<MessageDto> messageSlice = qrySvc.fetchMessages(convId, null, 0, 100).getContent();
        List<ChatMessage> chatMessages = messageSlice.stream()
                .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                .toList();

        /* 3️⃣ ChatGPT로 세밀 의도 JSON 추출 */
        String prompt = """
                사용자 메시지: "%s"
                의도(카테고리·스타일·색상·사이즈 등)를 아래 JSON으로 반환.
                예: { "category": "top", "color": "ivory", "style": "casual", "size": "M" }
                """.formatted(userMsg);
        String intentJson = gptSvc.completion(chatMessages, prompt)
                .block(Duration.ofSeconds(prop.timeoutSec()));

        log.debug("[GPT] extracted intent: {}", intentJson);

        /* 4️⃣ 사용자 선호(UserPreference) 업데이트 */
        // Note: Member는 messageSlice에서 가져올 수 없음. 다른 방법이 필요

        /* 5️⃣ 추천 서비스 호출 */
        List<ProductResponseDto> products = recSvc.recommend(userMsg);

        /* 6️⃣ 결과 패키징 */
        return RecommendationResponseDto.builder()
                .systemResponse("추출된 의도: " + intentJson)
                .recommendedProducts(products)
                .build();
    }
}