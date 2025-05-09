package com.example.crud.common.mapper;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.recommendation.domain.dto.ConversationDto;
import com.example.crud.ai.recommendation.domain.dto.MessageDto;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.stream.Collectors;

// 객체 간 변환 작업 Enttiy -> DTO
@Mapper(componentModel = "spring")
@Primary
public interface ConversationMapper {
    ConversationMapper INSTANCE = Mappers.getMapper(ConversationMapper.class);

    /*── Entity → DTO ──*/
    @Mapping(source = "member.number", target = "memberId")
    ConversationDto toDto(Conversation conv);

    @Mapping(source = "messageType", target = "role")
    MessageDto toDto(ConversationMessage msg);

    // ArrayList 변환을 위한 명시적 메서드 추가
    default List<MessageDto> messagesToDtos(List<ConversationMessage> messages) {
        return messages == null ? null
                : messages.stream().map(this::toDto).collect(Collectors.toList());
    }
}