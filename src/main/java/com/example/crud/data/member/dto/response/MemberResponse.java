package com.example.crud.data.member.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 회원 정보 응답 DTO
 *
 * 사용처:
 * - POST /register 응답
 * - GET /mypage 조회
 * - POST /mypage/profileEdit 응답
 *
 * 보안:
 * - password, roles 제외
 * - @JsonInclude: null 필드는 JSON에서 제외
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberResponse(
    Long number,
    String email,
    String name,
    String nickname,
    String phoneNumber,
    String address,
    String introduction
) {}
