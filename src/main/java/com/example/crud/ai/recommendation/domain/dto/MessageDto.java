package com.example.crud.ai.recommendation.domain.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")      // ZSet 내 중복 방지용 동등성 기준을 'id' 로 지정
public class MessageDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String role;              // "USER"/"ASSISTANT"/"SYSTEM"
    private String content;
    private LocalDateTime timestamp;
}