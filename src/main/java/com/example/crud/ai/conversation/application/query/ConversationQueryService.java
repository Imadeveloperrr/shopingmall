package com.example.crud.ai.conversation.application.query;

import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.conversation.domain.repository.ConversationMessageRepository;
import com.example.crud.ai.recommendation.domain.dto.MessageDto;
import com.example.crud.common.mapper.ConversationMapper;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationQueryService {

    private final RedisTemplate<String, MessageDto> redis;
    private final ConversationMessageRepository msgRepo;
    private final ConversationMapper mapper;

    @Transactional(readOnly = true)
    public Slice<MessageDto> fetchMessages(long convId,
                                           LocalDateTime cursorTs,
                                           long cursorId,
                                           int size) {

        String zKey = "conv:%d:msgs".formatted(convId);

        // 첫 페이지: Redis ZSET에서 최신 size 건
        if (cursorTs == null) {
            List<MessageDto> list = redis.opsForZSet()
                    .reverseRange(zKey, 0, size - 1)
                    .stream().toList();

            if (list.isEmpty()) {
                list = warmFromDb(zKey, convId, size);
            }
            return new SliceImpl<>(list, PageRequest.of(0, size), list.size() == size);
        }

        // 이후 페이지: 커서 점수 계산 후 SEEK
        double score = toScore(cursorTs, cursorId);
        Set<MessageDto> set = redis.opsForZSet()
                .reverseRangeByScore(zKey, score - 1e9, score - 1, 0, size);
        if (set.isEmpty()) {
            set = Set.copyOf(warmFromDb(zKey, convId, size, cursorTs));
        }
        List<MessageDto> list = new ArrayList<>(set);
        return new SliceImpl<>(list, PageRequest.of(0, size), list.size() == size);
    }

    /** DB → Redis 로딩 헬퍼 */
    private List<MessageDto> warmFromDb(String zKey,
                                        long convId,
                                        int size,
                                        LocalDateTime... before) {
        Pageable p = PageRequest.of(0, size, Sort.by("timestamp").descending());
        Page<ConversationMessage> page;

        if (before.length == 0) {
            // 첫 페이지 로딩
            page = msgRepo.findByConversation_Id(convId, p);
        } else {
            // cursor 이전 메시지 로딩
            page = msgRepo.findByConversation_IdAndTimestampBefore(convId, before[0], p);
        }

        // 엔티티 → DTO 매핑
        List<MessageDto> dto = page.map(mapper::toDto).getContent();

        // Redis ZSET에 점수와 함께 저장, TTL 설정
        dto.forEach(m ->
                redis.opsForZSet()
                        .add(zKey, m, toScore(m.getTimestamp(), m.getId()))
        );
        redis.expire(zKey, Duration.ofHours(6));

        return dto;
    }

    /** LocalDateTime + id → ZSET score 계산 (밀리초 단위 + id) */
    private static double toScore(LocalDateTime ts, long id) {
        long epoch = ts.toInstant(ZoneOffset.UTC).toEpochMilli();
        return epoch * 1e6 + (id % 1_000_000);
    }
}
