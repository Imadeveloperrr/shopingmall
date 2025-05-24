package com.example.crud.controller;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.repository.ConversationRepository;
import com.example.crud.ai.recommendation.application.ConversationalRecommendationService;
import com.example.crud.ai.recommendation.domain.dto.RecommendationResponseDto;
import com.example.crud.ai.recommendation.domain.dto.UserMessageRequestDto;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.entity.Member;
import com.example.crud.enums.ConversationStatus;
import com.example.crud.repository.MemberRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 대화형 상품 추천 API 컨트롤러
 */

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

    private final ConversationalRecommendationService recommendationService;
    private final ConversationRepository conversationRepository;
    private final MemberService memberService;
    private final MemberRepository memberRepository;

    /**
     * 새 대화 시작
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation(Authentication auth) {
        Member member = memberService.getMemberEntity(auth.getName());

        Conversation conversation = Conversation.builder()
                .member(member)
                .startTime(LocalDateTime.now())
                .status(ConversationStatus.ACTIVE)
                .lastUpdated(LocalDateTime.now())
                .build();

        conversation = conversationRepository.save(conversation);

        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversation.getId());
        response.put("message", "안녕하세요! 어떤 스타일의 상품을 찾고 계신가요?");

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 메시지 처리 및 추천
     */
    @PostMapping("/{conversationId}/message")
    public ResponseEntity<RecommendationResponseDto> sendMessage(
            @PathVariable Long conversationId,
            @Valid @RequestBody UserMessageRequestDto request,
            Authentication auth) {

        // 대화 권한 확인
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.getMember().getEmail().equals(auth.getName())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new IllegalArgumentException("종료된 대화입니다.");
        }

        // 메시지 처리 및 추천
        RecommendationResponseDto response = recommendationService.processUserMessage(conversationId, request.getMessage());

        return ResponseEntity.ok(response);
    }

    /**
     * 대화 종료
     */
    @PostMapping("/{conversationId}/end")
    public ResponseEntity<Map<String, String>> endConversation(
            @PathVariable Long conversationId,
            Authentication auth) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다."));

        if (!conversation.getMember().getEmail().equals(auth.getName())) {
            throw new IllegalStateException("권한이 없습니다.");
        }

        conversation.setStatus(ConversationStatus.COMPLETED);
        conversation.setLastUpdated(LocalDateTime.now());
        conversationRepository.save(conversation);

        Map<String, String> response = new HashMap<>();
        response.put("message", "대화가 종료되었습니다. 감사합니다!");

        return ResponseEntity.ok(response);
    }
}
