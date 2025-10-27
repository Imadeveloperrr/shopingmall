package com.example.crud.data.member.service.auth;

import com.example.crud.common.security.JwtToken;
import com.example.crud.data.token.TokenRequestDto;

/**
 * 회원 인증 서비스 인터페이스
 * - 확장 가능성: JWT, OAuth2, SAML 등 다양한 인증 방식 지원
 */
public interface MemberAuthService {

    /**
     * 로그인
     */
    JwtToken signIn(String username, String password, boolean rememberMe);

    /**
     * 토큰 재발급
     */
    JwtToken reissue(TokenRequestDto tokenRequestDto);
}
