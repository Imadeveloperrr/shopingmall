package com.example.crud.data.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 DTO
 *
 * 사용처: POST /mypage/changePassword
 *
 * 원칙: DTO는 순수 데이터만, 검증 로직은 Service에서
 */
public record ChangePasswordRequest(

    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    String currentPassword,

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    String newPassword,

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    String confirmPassword

) {}
