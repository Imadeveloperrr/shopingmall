package com.example.crud.data.ai.dto;

import com.example.crud.enums.MessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private MessageType messageType;
    private String content;
    private LocalDateTime timestamp;
}
