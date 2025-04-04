package com.example.crud.data.ai.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 사용자 메시지 입력을 위한 DTO입니다.
 * 실제 업무에서는 추가적인 필드(예: 사용자 ID, 타임스탬프 등)를 포함할 수 있습니다.
 */
@Data
public class UserMessageRequestDto {
    @NotBlank(message = "메시지는 필수 입력값입니다.")
    private String message;
}
