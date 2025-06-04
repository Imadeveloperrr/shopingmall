package com.example.crud.common.mapper;

import com.example.crud.ai.conversation.domain.entity.Conversation;
import com.example.crud.ai.conversation.domain.entity.ConversationMessage;
import com.example.crud.ai.recommendation.domain.dto.ConversationDto;
import com.example.crud.ai.recommendation.domain.dto.MessageDto;
import org.mapstruct.*;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.stream.Collectors;

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

    /* List 변환 - default 메서드로 구현 제공 */
    @Named("messagesToDtos")  // 이 어노테이션을 추가해야 합니다
    default List<MessageDto> messagesToDtos(List<ConversationMessage> messages) {
        if (messages == null) {
            return null;
        }
        return messages.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
}
