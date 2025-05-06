package com.example.crud.common.mapper;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.recommendation.domain.dto.ConversationDto;
import com.example.crud.ai.recommendation.domain.dto.MessageDto;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    ConversationMapper INSTANCE = Mappers.getMapper(ConversationMapper.class);

    /*── Entity → DTO ──*/
    @Mapping(source = "member.id", target = "memberId")
    ConversationDto toDto(Conversation conv);

    @Mapping(source = "messageType", target = "role")
    MessageDto toDto(ConversationMessage msg);
}