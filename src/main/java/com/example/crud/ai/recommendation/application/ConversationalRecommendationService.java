package com.example.crud.ai.recommendation.application;
import com.example.crud.ai.config.ChatGptProperties;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.conversation.application.query.ConversationQueryService;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.recommendation.domain.dto.*;
import com.example.crud.ai.recommendation.domain.KeywordCriteria;
import com.example.crud.modules.product.application.ProductQueryService;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {

    /*───────────────── 의존성 주입 ─────────────────*/
    private final ConversationCommandService cmdSvc;     // 메시지 저장
    private final ConversationQueryService   qrySvc;     // 대화 히스토리 조회
    private final ChatGptServiceLite         gptSvc;     // ChatGPT 호출
    private final EmbeddingClient            embedCli;   // FastAPI 임베딩
    private final RecommendationService      recSvc;     // Top-N 추천
    private final UserPreferenceRepository   prefRepo;   // 선호 저장
    private final ProductQueryService        productQry; // 상품 상세 변환
    private final ChatGptProperties          prop;       // temperature 등

    /*───────────────── 공개 메서드 ─────────────────*/
    @Transactional
    public RecommendationResponseDto processUserMessage(Long convId, String userMsg) {

        /* 1️⃣  사용자 메시지 저장 */
        cmdSvc.addMessage(convId, MessageType.USER, userMsg);

        /* 2️⃣  전체 대화 히스토리 조회 → ChatGPT 입력용 */
        List<ConversationMessage> history = qrySvc.getAllMessages(convId);

        /* 3️⃣  ChatGPT로 세밀 의도 JSON 추출 (Sync 5s 타임아웃) */
        String prompt = """
                사용자 메시지: "%s"
                의도(카테고리·스타일·색상·사이즈 등)를 아래 JSON 으로 반환.
                예: { "category": "top", "color": "ivory", "style": "casual", "size": "M" }
                """.formatted(userMsg);
        String intentJson = gptSvc.completion(
                        // ChatMessage 리스트 변환 util (생략)
                        ChatPayload.toChatMessages(history), prompt)
                .block(Duration.ofSeconds(prop.timeoutSec()));

        log.debug("[GPT] extracted intent: {}", intentJson);

        /* 4️⃣  사용자 선호(UserPreference) Upsert */
        var member = history.get(0).getConversation().getMember();
        var pref = prefRepo.findByMember_Id(member.getId())
                .orElseGet(() -> prefRepo.save(
                        new com.example.crud.ai.conversation.domain.entity.UserPreference(member)));
        pref.setPreferences(intentJson);                     // JSON 그대로 저장
        prefRepo.save(pref);

        /* 5️⃣  1차 후보(Keyword 필터) → 2차 임베딩 매칭 */
        KeywordCriteria criteria = KeywordCriteria.fromJson(intentJson);
        List<Long> candidateIds  = recSvc.findCandidateProductIds(criteria);   // 200~500건

        /* 5-2️⃣  사용자 쿼리 문장 임베딩 */
        float[] qVec = embedCli.embed(userMsg).block(Duration.ofSeconds(2));

        List<ProductMatch> topMatches = recSvc.rankByEmbedding(candidateIds, qVec, 20); // Top-20

        /* 6️⃣  상품 DTO 변환 */
        List<ProductResponseDto> products = productQry.toResponseDto(topMatches);

        /* 7️⃣  결과 패키징 */
        return RecommendationResponseDto.builder()
                .systemResponse("추출된 의도: " + intentJson)
                .recommendedProducts(products)
                .build();
    }
}
