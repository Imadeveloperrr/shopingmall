package com.example.crud.data.member.dto.request;

/**
 * 프로필 수정 요청 DTO
 *
 * 사용처: POST /mypage/profileEdit
 *
 * 부분 갱신:
 * - null이 아닌 필드만 업데이트
 * - Entity의 updateProfile 메서드에서 null 체크 수행
 */
public record UpdateProfileRequest(
    String name,
    String nickname,
    String phoneNumber,
    String address,
    String introduction
) {}
