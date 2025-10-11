package com.example.crud.controller;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.ConversationalRecommendationService;
import com.example.crud.ai.recommendation.domain.dto.RecommendationResponseDto;
import com.example.crud.ai.recommendation.domain.dto.UserMessageRequestDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.entity.Member;
import com.example.crud.enums.ConversationStatus;
import io.reactivex.Completable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationalRecommendationService crService;
    private final MemberService memberService;
    private final ConversationRepository cRepository;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation(Authentication auth) {
        try {
            Member member = memberService.getMemberEntity(auth.getName());

            Conversation conv = Conversation.builder()
                    .member(member)
                    .startTime(LocalDateTime.now())
                    .status(ConversationStatus.ACTIVE)
                    .lastUpdated(LocalDateTime.now())
                    .build();

            Long convid = cRepository.save(conv).getId();

            Map<String, Object> response = Map.of(
                    "conversationId", convid,
                    "message", "안녕하세요. 어떤 상품을 찾고 계시나요?"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "대화 시작 중 오류가 발생했습니다."));
        }
    }

    /**
     * 사용자 메시지 처리 및 추천
     */
    @PostMapping("/{conversationId}/message")
    public CompletableFuture<ResponseEntity<RecommendationResponseDto>> sendMessage(@PathVariable Long conversationId,
                                                                                    @Valid @RequestBody UserMessageRequestDto requestDto,
                                                                                    Authentication auth) {
        try {
            Conversation conv = cRepository.findByIdWithMember(conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

            if (!conv.getMember().getEmail().equals(auth.getName())) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.ok(errorResponse(conversationId, "권한이 없습니다."))
                );
            }

            if (conv.getStatus() != ConversationStatus.ACTIVE) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.ok(errorResponse(conversationId, "비활성화된 대화입니다."))
                );
            }

            return crService.processUserMessage(conversationId, requestDto.getMessage())
                    .thenApply(ResponseEntity::ok)
                    .exceptionally(e -> {
                        log.error("Controller 메시지 처리 실패", e);
                        return ResponseEntity.ok(errorResponse(conversationId, "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(errorResponse(conversationId, "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
            );
        }
    }

    @PostMapping("/{conversationId}/end")
    public ResponseEntity<Map<String, Object>> endConversation(@PathVariable Long conversationId, Authentication auth) {
        try {
            Conversation conv = cRepository.findByIdWithMember(conversationId).orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));
            if (!conv.getMember().getEmail().equals(auth.getName())) {
                return ResponseEntity.ok(Map.of("error", "권한이 없습니다."));
            }

            conv.setStatus(ConversationStatus.COMPLETED);
            conv.setLastUpdated(LocalDateTime.now());
            cRepository.save(conv);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "대화가 종료되었습니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
        catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "error",
                    "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }


    public RecommendationResponseDto errorResponse(Long conversationId, String message) {
        return RecommendationResponseDto.builder()
                .conversationId(conversationId)
                .aiResponse(message)
                .recommendations(List.of())
                .totalRecommendations(0)
                .build();
    }

}
