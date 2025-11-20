package com.example.crud.controller;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.ConversationalRecommendationService;
import com.example.crud.ai.recommendation.domain.dto.RecommendationResponseDto;
import com.example.crud.ai.recommendation.domain.dto.UserMessageRequestDto;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.service.find.MemberFindService;
import com.example.crud.entity.Member;
import com.example.crud.enums.ConversationStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationalRecommendationService crService;
    private final MemberFindService memberFindService;
    private final ConversationRepository cRepository;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation(Authentication auth) {
        Member member = memberFindService.getMemberEntity(auth.getName());

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
    }

    /**
     * 사용자 메시지 처리 및 추천
     */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<RecommendationResponseDto> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody UserMessageRequestDto requestDto,
            Authentication auth) {

        Conversation conv = cRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!conv.getMember().getEmail().equals(auth.getName())) {
            throw new BaseException(ErrorCode.CONVERSATION_UNAUTHORIZED);
        }

        if (conv.getStatus() != ConversationStatus.ACTIVE) {
            throw new BaseException(ErrorCode.CONVERSATION_INACTIVE);
        }

        RecommendationResponseDto response;
        try {
            response = crService.processUserMessage(conversationId, requestDto.getMessage()).join();
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.AI_SERVICE_UNAVAILABLE, ex.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{conversationId}/end")
    public ResponseEntity<Map<String, Object>> endConversation(@PathVariable Long conversationId, Authentication auth) {
        Conversation conv = cRepository.findById(conversationId)
                .orElseThrow(() -> new BaseException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conv.getMember().getEmail().equals(auth.getName())) {
            throw new BaseException(ErrorCode.CONVERSATION_UNAUTHORIZED);
        }

        conv.setStatus(ConversationStatus.COMPLETED);
        conv.setLastUpdated(LocalDateTime.now());
        cRepository.save(conv);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "대화가 종료되었습니다.");
        return ResponseEntity.ok(response);
    }

}
