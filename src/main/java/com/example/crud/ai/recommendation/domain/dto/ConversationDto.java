package com.example.crud.ai.recommendation.domain.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
public class ConversationDto {
    private Long                id;
    private Long                memberId;
    private LocalDateTime       startTime;
    private String              status;
    private LocalDateTime       lastUpdated;
    private List<MessageDto>    messages;
}
