package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.config.ChatGptProperties;
import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.conversation.application.query.ConversationQueryService;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.recommendation.domain.dto.*;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Member;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 대화형 상품 추천 서비스
 *
 * IntegratedRecommendationService를 사용하여
 * 대화 컨텍스트 기반의 추천을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationalRecommendationService {

    private final ConversationCommandService cmdSvc;
    private final ConversationQueryService qrySvc;
    private final ConversationRepository convRepo;
    private final ChatGptServiceLite gptSvc;
    private final IntegratedRecommendationService recommendationService; // 통합 서비스 사용
    private final UserPreferenceRepository prefRepo;
    private final ChatGptProperties prop;

    /**
     * 사용자 메시지 처리 및 추천 생성
     *
     * @param convId 대화 ID
     * @param userMsg 사용자 메시지
     * @return 추천 응답 (AI 메시지 + 상품 목록)
     */
    @Transactional
    public RecommendationResponseDto processUserMessage(Long convId, String userMsg) {
        log.info("대화 메시지 처리 시작: convId={}, message={}", convId, userMsg);

        // 1. 사용자 메시지 저장
        cmdSvc.addMessage(convId, MessageType.USER, userMsg);

        // 2. 대화 정보 조회
        Conversation conversation = convRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));
        Member member = conversation.getMember();

        // 3. 대화 컨텍스트 구축 (최근 10개 메시지)
        List<MessageDto> recentMessages = qrySvc.fetchMessages(convId, null, 0, 10).getContent();
        List<String> context = recentMessages.stream()
                .map(MessageDto::getContent)
                .collect(Collectors.toList());

        // 4. ChatGPT로 의도 분석
        Map<String, Object> intent = analyzeUserIntent(userMsg, context);

        // 5. 사용자 선호도 업데이트
        updateUserPreference(member, intent);

        // 6. 통합 추천 서비스로 추천 생성
        List<ProductResponseDto> recommendations = recommendationService
                .recommendWithContext(member.getNumber(), userMsg, context);

        // 7. AI 응답 생성
        String assistantResponse = generateAssistantResponse(intent, recommendations, userMsg);

        // 8. AI 응답 저장
        cmdSvc.addMessage(convId, MessageType.ASSISTANT, assistantResponse);

        // 9. 응답 DTO 생성 (실제 DTO 구조에 맞게)
        return RecommendationResponseDto.builder()
                .systemResponse(assistantResponse)
                .recommendedProducts(recommendations)
                .build();
    }

    /**
     * 특정 상품에 대한 추천
     */
    @Transactional
    public RecommendationResponseDto recommendSimilarProducts(Long convId, Long productId) {
        log.info("유사 상품 추천: convId={}, productId={}", convId, productId);

        // 대화 확인
        Conversation conversation = convRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));

        // 상품 정보로 추천 메시지 생성
        String message = String.format("상품 ID %d와 비슷한 상품 추천", productId);

        // IntegratedRecommendationService 사용
        List<ProductResponseDto> recommendations = recommendationService
                .recommend(conversation.getMember().getNumber(), message);

        String assistantResponse = String.format(
                "선택하신 상품과 비슷한 %d개의 상품을 찾았습니다. " +
                        "스타일, 가격대, 브랜드를 고려하여 추천드립니다.",
                recommendations.size()
        );

        return RecommendationResponseDto.builder()
                .systemResponse(assistantResponse)
                .recommendedProducts(recommendations)
                .build();
    }

    /**
     * 카테고리 기반 추천
     */
    @Transactional(readOnly = true)
    public RecommendationResponseDto recommendByCategory(Long convId, String category) {
        log.info("카테고리 추천: convId={}, category={}", convId, category);

        // 대화 확인
        Conversation conversation = convRepo.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));

        // IntegratedRecommendationService의 카테고리 추천 사용
        List<ProductResponseDto> recommendations = recommendationService
                .recommendByCategory(category, 20);

        String assistantResponse = String.format(
                "%s 카테고리에서 인기 있는 상품 %d개를 추천드립니다.",
                category, recommendations.size()
        );

        return RecommendationResponseDto.builder()
                .systemResponse(assistantResponse)
                .recommendedProducts(recommendations)
                .build();
    }

    /**
     * ChatGPT를 사용한 의도 분석
     */
    private Map<String, Object> analyzeUserIntent(String userMessage, List<String> context) {
        try {
            String prompt = buildIntentAnalysisPrompt(userMessage, context);

            // ChatPayload 생성 - 실제 구조에 맞게
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage("system", "당신은 쇼핑 도우미입니다. 사용자의 의도를 분석하고 JSON 형식으로 응답해주세요."));
            messages.add(new ChatMessage("user", prompt));

            // ChatGptServiceLite의 completion 메서드 사용
            String response = gptSvc.completion(messages, prompt)
                    .block(Duration.ofSeconds(5));

            return parseIntentResponse(response);

        } catch (Exception e) {
            log.error("의도 분석 실패", e);
            return getDefaultIntent();
        }
    }

    /**
     * 의도 분석 프롬프트 생성
     */
    private String buildIntentAnalysisPrompt(String userMessage, List<String> context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("다음 대화 컨텍스트와 사용자 메시지를 분석하여 의도를 파악해주세요.\n\n");

        if (!context.isEmpty()) {
            prompt.append("이전 대화:\n");
            context.stream()
                    .limit(5)
                    .forEach(msg -> prompt.append("- ").append(msg).append("\n"));
            prompt.append("\n");
        }

        prompt.append("현재 메시지: ").append(userMessage).append("\n\n");

        prompt.append("다음 JSON 형식으로 응답해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"intent\": \"browse|search|compare|purchase\",\n");
        prompt.append("  \"categories\": [\"카테고리1\", \"카테고리2\"],\n");
        prompt.append("  \"brands\": [\"브랜드1\"],\n");
        prompt.append("  \"priceRange\": {\"min\": 0, \"max\": 100000},\n");
        prompt.append("  \"keywords\": [\"키워드1\", \"키워드2\"],\n");
        prompt.append("  \"sentiment\": \"positive|neutral|negative\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * ChatGPT 응답 파싱
     */
    private Map<String, Object> parseIntentResponse(String response) {
        try {
            // JSON 부분만 추출
            int startIdx = response.indexOf("{");
            int endIdx = response.lastIndexOf("}") + 1;

            if (startIdx >= 0 && endIdx > startIdx) {
                String jsonStr = response.substring(startIdx, endIdx);
                return Json.decode(jsonStr, Map.class);
            }
        } catch (Exception e) {
            log.warn("의도 파싱 실패: {}", e.getMessage());
        }

        return getDefaultIntent();
    }

    /**
     * 기본 의도 반환
     */
    private Map<String, Object> getDefaultIntent() {
        Map<String, Object> intent = new HashMap<>();
        intent.put("intent", "browse");
        intent.put("categories", new ArrayList<>());
        intent.put("brands", new ArrayList<>());
        intent.put("priceRange", Map.of("min", 0, "max", 1000000));
        intent.put("keywords", new ArrayList<>());
        intent.put("sentiment", "neutral");
        return intent;
    }

    /**
     * 사용자 선호도 업데이트
     */
    private void updateUserPreference(Member member, Map<String, Object> intent) {
        try {
            // UserPreferenceRepository의 실제 메서드 사용
            UserPreference preference = prefRepo.findByMember_Number(member.getNumber())
                    .orElseGet(() -> {
                        UserPreference newPref = new UserPreference();
                        newPref.setMember(member);
                        newPref.setPreferences("{}");
                        newPref.setLastUpdated(LocalDateTime.now());
                        return newPref;
                    });

            // 기존 선호도 파싱
            Map<String, Object> currentPrefs = Json.decode(preference.getPreferences(), Map.class);

            // 새로운 의도와 병합
            Map<String, Object> mergedPrefs = mergePreferences(currentPrefs, intent);

            // 저장
            preference.setPreferences(Json.encode(mergedPrefs));
            preference.setLastUpdated(LocalDateTime.now());
            prefRepo.save(preference);

            log.debug("선호도 업데이트 완료: memberId={}", member.getNumber());

        } catch (Exception e) {
            log.error("선호도 업데이트 실패: memberId={}", member.getNumber(), e);
        }
    }

    /**
     * 선호도 병합
     */
    private Map<String, Object> mergePreferences(Map<String, Object> current, Map<String, Object> newIntent) {
        Map<String, Object> merged = new HashMap<>(current);

        // 카테고리 병합 (최대 10개)
        List<String> categories = mergeList(
                (List<String>) current.getOrDefault("categories", new ArrayList<>()),
                (List<String>) newIntent.getOrDefault("categories", new ArrayList<>()),
                10
        );
        merged.put("categories", categories);

        // 브랜드 병합 (최대 10개)
        List<String> brands = mergeList(
                (List<String>) current.getOrDefault("brands", new ArrayList<>()),
                (List<String>) newIntent.getOrDefault("brands", new ArrayList<>()),
                10
        );
        merged.put("brands", brands);

        // 가격 범위 업데이트
        Map<String, Integer> newPriceRange = (Map<String, Integer>) newIntent.get("priceRange");
        if (newPriceRange != null && !newPriceRange.isEmpty()) {
            Map<String, Integer> currentRange = (Map<String, Integer>)
                    merged.getOrDefault("priceRange", new HashMap<>());

            // 가격 범위 확장
            int currentMin = currentRange.getOrDefault("min", 0);
            int currentMax = currentRange.getOrDefault("max", 1000000);
            int newMin = newPriceRange.getOrDefault("min", currentMin);
            int newMax = newPriceRange.getOrDefault("max", currentMax);

            merged.put("priceRange", Map.of(
                    "min", Math.min(currentMin, newMin),
                    "max", Math.max(currentMax, newMax)
            ));
        }

        // 키워드 병합 (최대 20개)
        List<String> keywords = mergeList(
                (List<String>) current.getOrDefault("keywords", new ArrayList<>()),
                (List<String>) newIntent.getOrDefault("keywords", new ArrayList<>()),
                20
        );
        merged.put("keywords", keywords);

        // 메타 정보 업데이트
        merged.put("lastUpdated", LocalDateTime.now().toString());
        merged.put("updateCount", ((Integer) merged.getOrDefault("updateCount", 0)) + 1);

        return merged;
    }

    /**
     * 리스트 병합 (중복 제거, 최신성 우선)
     */
    private List<String> mergeList(List<String> current, List<String> newItems, int maxSize) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        // 새로운 항목을 먼저 추가 (최신성)
        merged.addAll(newItems);

        // 기존 항목 추가
        merged.addAll(current);

        // 크기 제한
        return merged.stream()
                .limit(maxSize)
                .collect(Collectors.toList());
    }

    /**
     * AI 어시스턴트 응답 생성
     */
    private String generateAssistantResponse(
            Map<String, Object> intent,
            List<ProductResponseDto> products,
            String userMessage) {

        String intentType = (String) intent.getOrDefault("intent", "browse");

        if (products.isEmpty()) {
            return generateNoResultResponse(intent);
        }

        StringBuilder response = new StringBuilder();

        // 의도에 따른 인사말
        switch (intentType) {
            case "search":
                response.append("검색하신 조건에 맞는 ");
                break;
            case "compare":
                response.append("비교해보실 만한 ");
                break;
            case "purchase":
                response.append("구매를 고려하실 만한 ");
                break;
            default:
                response.append("고객님께 추천드리는 ");
        }

        response.append(products.size()).append("개의 상품을 찾았습니다!\n\n");

        // 상위 3개 상품 하이라이트
        int highlightCount = Math.min(3, products.size());
        for (int i = 0; i < highlightCount; i++) {
            ProductResponseDto product = products.get(i);
            response.append(String.format("%d. **%s** - %s (%s)\n",
                    i + 1,
                    product.getName(),
                    product.getBrand(),
                    product.getPrice()  // getPrice()는 이미 포맷된 String
            ));

            if (product.getIntro() != null && !product.getIntro().isEmpty()) {
                response.append("   ").append(product.getIntro()).append("\n");
            }
            response.append("\n");
        }

        // 추가 안내
        response.append(generateAdditionalGuidance(intent, products.size()));

        return response.toString();
    }

    /**
     * 검색 결과 없음 응답
     */
    private String generateNoResultResponse(Map<String, Object> intent) {
        StringBuilder response = new StringBuilder();

        response.append("죄송합니다. 말씀하신 조건에 맞는 상품을 찾지 못했습니다.\n\n");

        // 의도에 따른 대안 제시
        List<String> categories = (List<String>) intent.getOrDefault("categories", new ArrayList<>());
        if (!categories.isEmpty()) {
            response.append("다른 ").append(categories.get(0)).append(" 상품을 보시겠어요?\n");
        }

        response.append("다음과 같이 검색 조건을 변경해보세요:\n");
        response.append("- 가격 범위를 넓혀보세요\n");
        response.append("- 다른 브랜드나 스타일을 시도해보세요\n");
        response.append("- 더 일반적인 키워드를 사용해보세요");

        return response.toString();
    }

    /**
     * 추가 안내 메시지 생성
     */
    private String generateAdditionalGuidance(Map<String, Object> intent, int resultCount) {
        StringBuilder guidance = new StringBuilder();

        if (resultCount > 3) {
            guidance.append("더 많은 상품은 아래에서 확인하실 수 있습니다.\n");
        }

        // 의도별 추가 안내
        String intentType = (String) intent.getOrDefault("intent", "browse");
        switch (intentType) {
            case "compare":
                guidance.append("상품을 클릭하시면 자세한 비교가 가능합니다.\n");
                break;
            case "purchase":
                guidance.append("장바구니에 담거나 바로 구매하실 수 있습니다.\n");
                break;
        }

        guidance.append("\n다른 조건으로 검색하시려면 말씀해주세요!");

        return guidance.toString();
    }
}