package com.example.crud.ai.conversation.application.search;

import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.conversation.domain.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL Full-text Search로 대화 검색 - Elasticsearch 대체
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSearchService {

    private final ConversationMessageRepository messageRepository;

    /**
     * 메시지 내용 검색 - PostgreSQL LIKE 사용
     */
    public Page<ConversationMessage> searchMessages(Long userId, String query, int page, int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            
            if (query == null || query.trim().isEmpty()) {
                return messageRepository.findByConversationMemberNumberOrderByCreatedAtDesc(
                    userId, pageRequest);
            }

            // 간단한 LIKE 검색으로 충분
            String searchQuery = "%" + query.trim() + "%";
            return messageRepository.findByConversationMemberNumberAndContentContainingIgnoreCaseOrderByCreatedAtDesc(
                userId, searchQuery, pageRequest);

        } catch (Exception e) {
            log.error("메시지 검색 실패: userId={}, query={}", userId, query, e);
            return Page.empty();
        }
    }

    /**
     * 최근 대화 조회
     */
    public Page<ConversationMessage> getRecentMessages(Long userId, int page, int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            return messageRepository.findByConversationMemberNumberOrderByCreatedAtDesc(
                userId, pageRequest);

        } catch (Exception e) {
            log.error("최근 메시지 조회 실패: userId={}", userId, e);
            return Page.empty();
        }
    }
}