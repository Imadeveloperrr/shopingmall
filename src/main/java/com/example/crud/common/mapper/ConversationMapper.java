package com.example.crud.common.mapper;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.recommendation.domain.dto.ConversationDto;
import com.example.crud.ai.recommendation.domain.dto.MessageDto;
import org.mapstruct.*;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Mapper(componentModel = "spring")
@Primary
public interface ConversationMapper {

    /* Conversation → ConversationDto */
    @Mapping(source = "member.number", target = "memberId")
    @Mapping(source = "messages", target = "messages", qualifiedByName = "messagesToDtos")
    ConversationDto toDto(Conversation conv);

    /* ConversationMessage → MessageDto */
    @Mapping(source = "messageType", target = "role")
    MessageDto toDto(ConversationMessage msg);

    /* List 변환 */
    @Named("messagesToDtos")                               // 이름 맞춤
    default List<MessageDto> messagesToDtos(List<ConversationMessage> list) {
        return list == null ? null
                : list.stream().map(this::toDto).toList();
    }
}
