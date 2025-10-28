package com.example.crud.data.member.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * 회원 정보 응답 DTO
 *
 * 사용처:
 * - POST /register 응답
 * - GET /mypage 조회
 * - POST /mypage/profileEdit 응답
 *
 * 설계:
 * - number 필드 유지 (프론트엔드 호환성)
 * - password 절대 포함 안 함 (보안)
 * - @JsonInclude: null 필드는 JSON 응답에서 제외
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberResponse {

    private Long number;
    private String email;
    private String name;
    private String nickname;
    private String phoneNumber;
    private String address;
    private String introduction;
}
