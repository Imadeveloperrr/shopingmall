package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.UserPreference;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.conversation.application.command.ConversationCommandService;
import com.example.crud.ai.conversation.application.query.ConversationQueryService;
import com.example.crud.ai.conversation.domain.repository.UserPreferenceRepository;
import com.example.crud.ai.embedding.infrastructure.ChatGptServiceLite;
import com.example.crud.ai.recommendation.domain.dto.*;
import com.example.crud.common.utility.Json;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Member;
import com.example.crud.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 적응형 하이브리드 대화형 상품 추천 서비스
 *
 * 실용적인 하이브리드 전략:
 * 1. 초고속 복잡도 판단 (1-5ms)
 * 2. 복잡도에 따른 적응형 처리
 * 3. 스마트 캐싱으로 중복 분석 방지
 * 4. 비용과 성능의 최적 균형
 *
 * 처리 방식:
 * - 단순 (70%): 규칙 기반만 사용 (~50ms)
 * - 중간 (25%): AI 선택적 사용 (~500ms)
 * - 복잡 (5%): 풀 AI + 병렬 처리 (~1500ms)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConversationalRecommendationService {

    private final ConversationCommandService cmdSvc;
    private final ConversationQueryService qrySvc;
    private final ConversationRepository convRepo;
    private final RecommendationEngine recommendationEngine;  // ← 새로운 추천 엔진
    private final UserPreferenceRepository prefRepo;
    private final ChatGptServiceLite chatGptService;

    /**
     * 사용자 메시지 처리 및 추천 생성
     */
    @Transactional
    public RecommendationResponseDto processUserMessage(Long convId, String userMsg) {
        log.info("대화 메시지 처리 시작: convId={}, message={}", convId, userMsg);

        try {
            // 1. 대화 정보 조회
            Conversation conversation = convRepo.findById(convId)
                    .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));

            Member member = conversation.getMember();
            if (member == null) {
                throw new IllegalStateException("회원 정보가 없습니다");
            }

            // 2. 사용자 메시지 저장
            cmdSvc.addMessage(convId, MessageType.USER, userMsg);

            // 3. 대화 컨텍스트 구축
            List<String> context = buildConversationContext(convId);

            // 4. 의도 분석 (GPT 기반)
            Map<String, Object> intent = analyzeIntent(userMsg, context);

            // 5. 사용자 선호도 업데이트 (비동기)
            CompletableFuture.runAsync(() -> updateUserPreference(member, intent));

            // 6. 추천 생성 (추천 엔진에 위임)
            List<ProductResponseDto> recommendations = recommendationEngine
                    .recommendWithContext(member.getNumber(), userMsg, context);

            // 7. AI 응답 생성 (GPT 기반)
            String assistantResponse;
                assistantResponse = chatGptService.completion(
                        List.of(new ChatMessage("system", "너는 친근한 쇼핑몰 상품 추천 전문가야. 사용자에게 자연스럽고 도움이 되는 답변을 해줘."),
                                new ChatMessage("user", userMsg)), "").block();

            // 8. AI 응답 저장
            cmdSvc.addMessage(convId, MessageType.ASSISTANT, assistantResponse);

            return RecommendationResponseDto.builder()
                    .systemResponse(assistantResponse)
                    .recommendedProducts(recommendations)
                    .build();

        } catch (Exception e) {
            log.error("메시지 처리 실패: convId={}", convId, e);
            // 에러 응답
            return RecommendationResponseDto.builder()
                    .systemResponse("죄송합니다. 추천을 생성하는 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.")
                    .recommendedProducts(new ArrayList<>())
                    .build();
        }
    }

    /**
     * 특정 상품에 대한 유사 상품 추천
     */
    @Transactional
    public RecommendationResponseDto recommendSimilarProducts(Long convId, Long productId) {
        log.info("유사 상품 추천: convId={}, productId={}", convId, productId);

        try {
            // 대화 확인
            Conversation conversation = convRepo.findById(convId)
                    .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));

            // 유사 상품 추천 메시지 생성
            String message = String.format("상품 ID %d와 비슷한 상품을 추천해주세요", productId);

            // 추천 생성
            List<ProductResponseDto> recommendations = recommendationEngine
                    .recommend(conversation.getMember().getNumber(), message);

            // 응답 생성
            String response = generateSimilarProductResponse(recommendations, productId);

            return RecommendationResponseDto.builder()
                    .systemResponse(response)
                    .recommendedProducts(recommendations)
                    .build();

        } catch (Exception e) {
            log.error("유사 상품 추천 실패", e);
            return createErrorResponse("유사 상품을 찾는 중 문제가 발생했습니다.");
        }
    }

    /**
     * 카테고리 기반 추천
     */
    @Transactional(readOnly = true)
    public RecommendationResponseDto recommendByCategory(Long convId, String category) {
        log.info("카테고리 추천: convId={}, category={}", convId, category);

        try {
            // 대화 확인
            Conversation conversation = convRepo.findById(convId)
                    .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + convId));

            // 카테고리 추천
            List<ProductResponseDto> recommendations = recommendationEngine
                    .recommendByCategory(category, 20);

            // 응답 생성
            String response = generateCategoryResponse(category, recommendations);

            return RecommendationResponseDto.builder()
                    .systemResponse(response)
                    .recommendedProducts(recommendations)
                    .build();

        } catch (Exception e) {
            log.error("카테고리 추천 실패", e);
            return createErrorResponse("카테고리별 추천을 가져오는 중 문제가 발생했습니다.");
        }
    }

    /**
     * 대화 컨텍스트 구축
     */
    private List<String> buildConversationContext(Long convId) {
        try {
            // 최근 10개 메시지 조회
            List<MessageDto> recentMessages = qrySvc.fetchMessages(convId, null, 0, 10)
                    .getContent();

            return recentMessages.stream()
                    .filter(msg -> "USER".equals(msg.getRole())) // role은 String
                    .map(MessageDto::getContent)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("대화 컨텍스트 구축 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * 의도 분석 (GPT 기반)
     */
    private Map<String, Object> analyzeIntent(String message, List<String> context) {
        Map<String, Object> intent = new HashMap<>();

        String gptResponse = chatGptService.completion(List.of(
                new ChatMessage("system",
                        "사용자의 의도와, 키워드, 가격범위, 카테고리를 다음과 같은 json 형태로 반환해줘. " +
                                "intent는 search, compare, similar, price, purchase 중에 하나. "
                                +
                                "{\n" +
                                "  \"intent\": \"search\",\n" +
                                "  \"keywords\": [\"원피스\", \"예쁜\"],\n" +
                                "  \"priceRange\": {\n" +
                                "    \"min\": 50000,\n" +
                                "    \"max\": 100000\n" +
                                "  },\n" +
                                "  \"categories\": [\"의류\"]\n" +
                                "}"),
                new ChatMessage("user", message)
                ), "").block();
        Map<String, Object> gptResult = Json.decode(gptResponse, Map.class);

        intent.put("intent", gptResult.get("intent"));
        intent.put("message", message);

        // 키워드 추출
        intent.put("keywords", gptResult.get("keywords"));

        // 가격 범위 추출
        @SuppressWarnings("unchecked")
        Map<String, Object> priceRange = (Map<String, Object>) gptResult.get("priceRange");
        if (priceRange != null) {
            intent.put("minPrice", priceRange.get("min"));
            intent.put("maxPrice", priceRange.get("max"));
        }

        // 카테고리 추출
        intent.put("category", gptResult.get("categories"));

        return intent;
    }


    /**
     * 사용자 선호도 업데이트
     */
    private void updateUserPreference(Member member, Map<String, Object> intent) {
        try {
            UserPreference preference = prefRepo.findByMember_Number(member.getNumber())
                    .orElseGet(() -> UserPreference.builder()
                            .member(member)
                            .preferences("{}")
                            .lastUpdated(LocalDateTime.now())
                            .build());

            // 기존 선호도 파싱
            Map<String, Object> prefs = Json.decode(preference.getPreferences(), Map.class);
            if (prefs == null) {
                prefs = new HashMap<>();
            }

            // 의도에서 선호도 정보 추출
            updatePreferenceFromIntent(prefs, intent);

            // 저장
            preference.setPreferences(Json.encode(prefs));
            preference.setLastUpdated(LocalDateTime.now());
            prefRepo.save(preference);

        } catch (Exception e) {
            log.error("선호도 업데이트 실패", e);
        }
    }

    /**
     * 의도에서 선호도 정보 업데이트
     */
    private void updatePreferenceFromIntent(Map<String, Object> prefs, Map<String, Object> intent) {
        // 카테고리 업데이트
        List<String> categories = (List<String>) intent.get("categories");
        if (categories != null && !categories.isEmpty()) {
            List<String> existingCategories = (List<String>) prefs.getOrDefault("categories", new ArrayList<>());
            Set<String> mergedCategories = new LinkedHashSet<>(categories);
            mergedCategories.addAll(existingCategories);
            prefs.put("categories", new ArrayList<>(mergedCategories));
        }

        // 가격 범위 업데이트
        Integer minPrice = (Integer) intent.get("minPrice");
        Integer maxPrice = (Integer) intent.get("maxPrice");
        if (minPrice != null || maxPrice != null) {
            prefs.put("minPrice", minPrice);
            prefs.put("maxPrice", maxPrice);
        }

        // 키워드 업데이트
        List<String> keywords = (List<String>) intent.get("keywords");
        if (keywords != null && !keywords.isEmpty()) {
            List<String> existingKeywords = (List<String>) prefs.getOrDefault("keywords", new ArrayList<>());
            Set<String> mergedKeywords = new LinkedHashSet<>(keywords);
            mergedKeywords.addAll(existingKeywords);

            // 최대 50개까지만 유지
            List<String> limitedKeywords = mergedKeywords.stream()
                    .limit(50)
                    .collect(Collectors.toList());
            prefs.put("keywords", limitedKeywords);
        }

        // 마지막 업데이트 시간
        prefs.put("lastUpdated", LocalDateTime.now().toString());
    }

    /**
     * AI 응답 생성
     */
    private String generateResponse(Map<String, Object> intent,
                                    List<ProductResponseDto> products,
                                    String userMessage) {

        if (products.isEmpty()) {
            return generateNoResultResponse(intent);
        }

        StringBuilder response = new StringBuilder();
        String intentType = (String) intent.getOrDefault("intent", "browse");

        // 의도에 따른 인사말
        response.append(getIntentGreeting(intentType, products.size()));
        response.append("\n\n");

        // 상위 3개 상품 하이라이트
        int highlightCount = Math.min(3, products.size());
        for (int i = 0; i < highlightCount; i++) {
            ProductResponseDto product = products.get(i);
            response.append(formatProductHighlight(i + 1, product));
        }

        // 추가 안내
        response.append(generateAdditionalGuidance(intent, products));

        return response.toString();
    }

    /**
     * 의도별 인사말
     */
    private String getIntentGreeting(String intentType, int productCount) {
        switch (intentType) {
            case "search":
                return String.format("검색하신 조건에 맞는 %d개의 상품을 찾았습니다!", productCount);
            case "compare":
                return String.format("비교해보실 만한 %d개의 상품을 준비했습니다.", productCount);
            case "similar":
                return String.format("비슷한 스타일의 상품 %d개를 추천드립니다.", productCount);
            case "price":
                return String.format("가격 조건에 맞는 %d개의 상품입니다.", productCount);
            case "purchase":
                return String.format("구매를 고려하실 만한 %d개의 상품을 엄선했습니다!", productCount);
            default:
                return String.format("고객님께 추천드리는 %d개의 상품입니다.", productCount);
        }
    }

    /**
     * 상품 하이라이트 포맷
     */
    private String formatProductHighlight(int index, ProductResponseDto product) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("%d. **%s** - %s\n",
                index, product.getName(), product.getBrand()));
        sb.append(String.format("   💰 %s", product.getPrice()));

        // 카테고리 정보 추가
        if (product.getCategory() != null) {
            sb.append(" | 📂 ").append(product.getCategory());
        }

        if (product.getIntro() != null && !product.getIntro().isEmpty()) {
            sb.append("\n   📝 ").append(product.getIntro());
        }

        sb.append("\n\n");
        return sb.toString();
    }

    /**
     * 추가 안내 생성
     */
    private String generateAdditionalGuidance(Map<String, Object> intent,
                                              List<ProductResponseDto> products) {
        StringBuilder guidance = new StringBuilder("\n");

        // 가격 범위가 있는 경우
        if (intent.containsKey("minPrice") || intent.containsKey("maxPrice")) {
            guidance.append("💡 가격 필터가 적용되었습니다. ");
            guidance.append("다른 가격대도 보시려면 말씀해주세요.\n");
        }

        // 추가 추천 가능 여부
        if (products.size() >= 10) {
            guidance.append("📌 더 많은 상품을 보시려면 '더 보여줘'라고 말씀해주세요.\n");
        }

        // 관련 카테고리 안내
        List<String> categories = (List<String>) intent.get("categories");
        if (categories != null && !categories.isEmpty()) {
            guidance.append("🏷️ 관련 카테고리: ").append(String.join(", ", categories)).append("\n");
        }

        return guidance.toString();
    }

    /**
     * 검색 결과 없음 응답
     */
    private String generateNoResultResponse(Map<String, Object> intent) {
        StringBuilder response = new StringBuilder();

        response.append("죄송합니다. 말씀하신 조건에 정확히 맞는 상품을 찾지 못했습니다. 😔\n\n");

        // 대안 제시
        response.append("다음과 같은 방법을 시도해보시는 건 어떨까요?\n");
        response.append("• 검색 조건을 조금 넓혀보기\n");
        response.append("• 다른 카테고리 둘러보기\n");
        response.append("• 인기 상품 확인하기\n\n");

        response.append("도움이 필요하시면 언제든 말씀해주세요!");

        return response.toString();
    }

    /**
     * 유사 상품 응답 생성
     */
    private String generateSimilarProductResponse(List<ProductResponseDto> products, Long productId) {
        if (products.isEmpty()) {
            return "죄송합니다. 현재 유사한 상품을 찾을 수 없습니다. 다른 상품을 확인해보시겠어요?";
        }

        return String.format(
                "선택하신 상품과 비슷한 %d개의 상품을 찾았습니다! 🎯\n" +
                        "스타일, 가격대, 브랜드를 고려하여 엄선했습니다.\n\n" +
                        "마음에 드는 상품이 있으신가요?",
                products.size()
        );
    }

    /**
     * 카테고리 응답 생성
     */
    private String generateCategoryResponse(String category, List<ProductResponseDto> products) {
        if (products.isEmpty()) {
            return String.format("%s 카테고리에 현재 추천할 상품이 없습니다. 다른 카테고리를 확인해보시겠어요?", category);
        }

        return String.format(
                "%s 카테고리에서 인기 있는 상품 %d개를 추천드립니다! 🛍️\n" +
                        "최신 트렌드와 판매량을 기준으로 선정했습니다.",
                category, products.size()
        );
    }

    /**
     * 에러 응답 생성
     */
    private RecommendationResponseDto createErrorResponse(String message) {
        return RecommendationResponseDto.builder()
                .systemResponse(message)
                .recommendedProducts(new ArrayList<>())
                .build();
    }

    // ========== 호환성 메서드들 (외부 컴포넌트용) ==========
    
    /**
     * 단순 추천 생성 (이벤트 프로세서용)
     */
    public List<ProductResponseDto> recommend(Long userId, String message) {
        return recommendationEngine.recommend(userId, message);
    }

    /**
     * 컨텍스트와 함께 추천 생성 (이벤트 프로세서용)
     */
    public List<ProductResponseDto> recommendWithContext(Long userId, String message, List<String> context) {
        return recommendationEngine.recommendWithContext(userId, message, context);
    }
}