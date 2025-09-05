package com.example.crud.ai.conversation.application.event;

import com.example.crud.ai.conversation.domain.event.MessageCreatedEvent;
import com.example.crud.ai.conversation.domain.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 대화 관련 이벤트 처리 - 간소화된 버전
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationEventHandler {

    private final ConversationMessageRepository messageRepository;

    /**
     * 메시지 생성 이벤트 처리 - 로깅만 수행
     */
    @EventListener
    @Async
    public void handleMessageCreated(MessageCreatedEvent event) {
        try {
            log.info("[Message] Created: id={}, userId={}, type={}", 
                event.getMessageId(), event.getUserId(), event.getMessageType());
                
            // 필요시 추가 처리 (메트릭, 로깅 등)
            // 복잡한 ES 인덱싱 대신 간단한 로깅
            
        } catch (Exception e) {
            log.error("[Message] 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 사용자 메시지 처리 - 간단한 분석
     */
    @EventListener
    @Async
    public void handleUserMessage(MessageCreatedEvent event) {
        try {
            if ("USER".equals(event.getMessageType()) && event.getContent() != null) {
                // 복잡한 ChatGPT 분석 대신 간단한 키워드 추출
                String content = event.getContent();
                log.debug("[Analysis] 사용자 메시지 분석: userId={}, length={}", 
                    event.getUserId(), content.length());
                    
                // 추후 필요시 간단한 키워드 기반 선호도 업데이트 가능
            }

        } catch (Exception e) {
            log.error("[Analysis] 메시지 분석 실패: {}", e.getMessage(), e);
        }
    }
}