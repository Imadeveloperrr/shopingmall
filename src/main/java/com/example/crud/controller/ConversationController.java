package com.example.crud.controller;

import com.example.crud.data.ai.service.impl.ConversationalRecommendationService;
import com.example.crud.entity.Conversation;
import com.example.crud.entity.ConversationMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ConversationController {

    private final ConversationalRecommendationService conversationalRecommendationService;
    private final ConversationService conversationService;

    /**
     * 사용자 메시지를 받아 해당 대화에 추가하고, 추천 결과를 반환합니다.
     *
     * - 입력값 검증(@Valid)과 로깅, 예외 처리를 통해 안정성을 확보합니다.
     * - 실제 서비스에서는 메시지 저장 및 추천 결과 산출이 트랜잭션 단위로 처리됩니다.
     *
     * @param conversationId 대화 ID
     * @param request        사용자 메시지 요청 DTO (UserMessageRequestDto)
     * @return 추천 결과 DTO (RecommendationResponseDto)
     */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<RecommendationResponseDto> processUserMessage(
            @PathVariable("conversationId") Long conversationId,
            @Valid @RequestBody UserMessageRequestDto request) {
        log.info("대화 ID {}에 사용자 메시지 수신: {}", conversationId, request.getMessage());
        RecommendationResponseDto responseDto = conversationalRecommendationService
                .processUserMessage(conversationId, request.getMessage());
        return ResponseEntity.ok(responseDto);
    }

    /**
     * 지정된 대화 ID에 해당하는 대화 내역을 조회합니다.
     *
     * - 대화 내역이 존재하지 않을 경우 HTTP 404(Not Found)를 반환합니다.
     * - 도메인 객체를 DTO로 변환하여 클라이언트에 응답합니다.
     *
     * @param conversationId 대화 ID
     * @return 대화 DTO (ConversationDto)
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable("conversationId") Long conversationId) {
        List<ConversationMessage> messages = conversationService.getConversationMessages(conversationId);
        if (messages == null || messages.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ConversationDto conversationDto = convertToDto(messages);
        return ResponseEntity.ok(conversationDto);
    }

    /**
     * 도메인 객체(Conversation, ConversationMessage)를 DTO(ConversationDto, MessageDto)로 변환합니다.
     *
     * @param messages 대화 메시지 리스트
     * @return ConversationDto
     */
    private ConversationDto convertToDto(List<ConversationMessage> messages) {
        Conversation conversation = messages.get(0).getConversation();
        ConversationDto dto = new ConversationDto();
        dto.setId(conversation.getId());
        dto.setMemberId(conversation.getMember().getNumber());
        dto.setStartTime(conversation.getStartTime());
        dto.setStatus(conversation.getStatus() != null ? conversation.getStatus().name() : null);
        List<MessageDto> messageDtos = messages.stream().map(message -> {
            MessageDto mDto = new MessageDto();
            mDto.setId(message.getId());
            mDto.setMessageType(message.getMessageType());
            mDto.setContent(message.getContent());
            mDto.setTimestamp(message.getTimestamp());
            return mDto;
        }).collect(Collectors.toList());
        dto.setMessages(messageDtos);
        return dto;
    }
}
