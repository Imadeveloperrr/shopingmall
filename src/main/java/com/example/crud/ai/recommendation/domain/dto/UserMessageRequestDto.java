package com.example.crud.ai.recommendation.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMessageRequestDto {
    @NotBlank(message = "message must not be blank")
    private String message;
}
