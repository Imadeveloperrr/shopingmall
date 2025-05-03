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

/**
 * ConversationQueryService
 * ------------------------
 * 대화 기록을 Redis와 DB에서 읽어와 페이징(슬라이스) 형태로 제공하는 서비스 클래스
 *
 * 주요 개념:
 * - Redis ZSET: 점수(score)를 기준으로 정렬 가능한 자료구조
 * - Slice: 전체 페이지 수를 알 필요 없이 "다음 페이지 존재 여부"만 제공하는 페이징 인터페이스
 * - Cursor-based pagination: 마지막으로 읽은 위치(시간+ID)를 기준으로 다음 데이터를 조회
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationQueryService {

    // RedisTemplate<String, MessageDto>를 이용해 Redis에 DTO를 직렬화/역직렬화하여 저장
    private final RedisTemplate<String, MessageDto> redis;
    private final ConversationMessageRepository msgRepo;  // JPA Repository
    private final ConversationMapper mapper;              // 엔티티 ↔ DTO 변환기

    /**
     * 대화 메시지를 슬라이스 형태로 조회
     *
     * @param convId    대화 ID
     * @param cursorTs  마지막으로 조회한 메시지의 timestamp (첫 페이지면 null)
     * @param cursorId  마지막으로 조회한 메시지의 ID  (첫 페이지면 0)
     * @param size      한 번에 조회할 메시지 개수
     * @return          메시지 DTO들의 Slice, 다음 페이지 존재 여부 포함
     */
    @Transactional(readOnly = true)
    public Slice<MessageDto> fetchMessages(long convId,
                                           LocalDateTime cursorTs,
                                           long cursorId,
                                           int size) {

        // Redis ZSET key 포맷: conv:{conversationId}:msgs
        String zKey = "conv:%d:msgs".formatted(convId);

        // 1) 첫 페이지 조회: cursorTs가 null인 경우
        if (cursorTs == null) {
            // Redis ZSET에서 score 내림차순(reverseRange)으로 상위 size개 가져오기
            List<MessageDto> list = redis.opsForZSet()
                    .reverseRange(zKey, 0, size - 1)
                    .stream().toList();

            // Redis에 데이터가 없으면 DB에서 최초 로딩(warm-up)
            if (list.isEmpty()) {
                list = warmFromDb(zKey, convId, size);
            }
            // SliceImpl: PageRequest는 단순 페이징 정보, 섹션 끝 여부(list.size()==size)로 다음 페이지 존재 판단
            return new SliceImpl<>(list, PageRequest.of(0, size), list.size() == size);
        }

        // 2) 이후 페이지 조회: cursorTs, cursorId로 score 계산 후 ZSET에서 seek
        double score = toScore(cursorTs, cursorId);
        // reverseRangeByScore: high score부터 score-1까지, offset 0, limit size
        Set<MessageDto> set = redis.opsForZSet()
                .reverseRangeByScore(zKey, score - 1e9, score - 1, 0, size);

        // Redis에도 없으면 DB에서 추가 로딩
        if (set.isEmpty()) {
            set = Set.copyOf(warmFromDb(zKey, convId, size, cursorTs));
        }
        // Set을 List로 변환
        List<MessageDto> list = new ArrayList<>(set);
        return new SliceImpl<>(list, PageRequest.of(0, size), list.size() == size);
    }

    /**
     * DB에서 메시지를 조회하여 Redis ZSET에 적재하고 DTO 리스트를 반환합니다.
     *
     * @param zKey      Redis ZSET key
     * @param convId    대화 ID
     * @param size      조회할 메시지 수
     * @param before    (Optional) before[0]이 제공되면 해당 timestamp 이전 메시지 조회
     */
    private List<MessageDto> warmFromDb(String zKey,
                                        long convId,
                                        int size,
                                        LocalDateTime... before) {
        // 페이징 정의: 최신순 내림차순 정렬
        Pageable p = PageRequest.of(0, size, Sort.by("timestamp").descending());
        Page<ConversationMessage> page;

        if (before.length == 0) {
            // 첫 페이지 로딩: 대화 ID로 전체 최신 메시지 가져오기
            page = msgRepo.findByConversation_Id(convId, p);
        } else {
            // 커서 기반 로딩: timestamp가 before[0] 이전인 메시지 가져오기
            page = msgRepo.findByConversation_IdAndTimestampBefore(convId, before[0], p);
        }

        // 엔티티 → DTO 변환
        List<MessageDto> dto = page.map(mapper::toDto).getContent();

        // Redis ZSET에 (member=MessageDto, score=toScore) 형태로 저장
        dto.forEach(m ->
                redis.opsForZSet()
                        .add(zKey, m, toScore(m.getTimestamp(), m.getId()))
        );
        // TTL 설정: 6시간 후 만료
        redis.expire(zKey, Duration.ofHours(6));

        return dto;
    }

    /**
     * LocalDateTime과 메시지 ID를 합쳐 ZSET score(double)로 변환합니다.
     * - epoch milli에 ID 하위 6자리 결합 → 시간 정밀도 보장 + ID 순서 보장
     *
     * @param ts    메시지 생성 시각
     * @param id    메시지 고유 ID
     * @return      double 형태의 score
     */
    private static double toScore(LocalDateTime ts, long id) {
        // UTC 기준 epoch milli
        long epoch = ts.toInstant(ZoneOffset.UTC).toEpochMilli();
        // epoch * 1e6 + (id % 1_000_000): 마지막 6자리로 ID 구별
        return epoch * 1e6 + (id % 1_000_000);
    }
}

