package com.example.crud.data.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationDto {

    private Long id;
    private Long memberId;
    private LocalDateTime startTime;
    private String status;
    private List<MessageDto> messages;
}
