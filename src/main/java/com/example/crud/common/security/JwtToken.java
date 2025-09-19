package com.example.crud.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * JWT 토큰 정보를 담는 데이터 클래스
 * Access Token과 Refresh Token을 함께 관리
 */
@Builder
@Data
@AllArgsConstructor
public class JwtToken {

    /** 인증 타입 (Bearer 방식 사용) */
    private String grantType;

    /** 실제 인증에 사용되는 Access Token (짧은 만료시간) */
    private String accessToken;

    /** Token 갱신에 사용되는 Refresh Token (긴 만료시간) */
    private String refreshToken;
}

