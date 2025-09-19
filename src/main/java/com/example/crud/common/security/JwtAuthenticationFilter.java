package com.example.crud.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 * HTTP 요청마다 JWT 토큰을 검증하고 Spring Security Context에 인증 정보를 설정
 * OncePerRequestFilter를 상속하여 요청당 한 번만 실행됨을 보장
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * HTTP 요청에서 JWT 토큰을 추출하여 인증 처리
     * 유효한 토큰이 있으면 SecurityContext에 인증 정보를 설정
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        // 요청에서 JWT 토큰 추출
        String token = resolveToken(request);

        try {
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                // 유효한 토큰으로부터 인증 정보 추출 및 설정
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                if (authentication != null) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } else {
                // 무효한 토큰: 쿠키 청소 및 인증 정보 제거
                clearTokenCookies(response);
                SecurityContextHolder.clearContext();
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        // 다음 필터로 전달
        filterChain.doFilter(request, response);
    }

    /**
     * 무효한 토큰 발견 시 쿠키에서 토큰 제거
     * MaxAge를 0으로 설정하여 쿠키를 삭제
     */
    private void clearTokenCookies(HttpServletResponse response) {
        // Access Token 쿠키 삭제
        Cookie accessTokenCookie = new Cookie("accessToken", null);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        // Refresh Token 쿠키 삭제
        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
    }

    /**
     * 필터를 적용하지 않을 요청 경로 정의
     * 인증이 필요없는 공개 자원에 대해서는 필터 스킨
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getServletPath();
        return path.startsWith("/login") ||       // 로그인 페이지
               path.startsWith("/register") ||   // 회원가입 페이지
               path.startsWith("/static/") ||    // 정적 리소스
               path.startsWith("/css/") ||       // CSS 파일
               path.startsWith("/js/") ||        // JavaScript 파일
               path.startsWith("/img/") ||       // 이미지 파일
               path.equals("/favicon.ico");       // 파비콘
    }

    /**
     * HTTP 요청에서 JWT 토큰을 추출
     * 1순위: Authorization 헤더의 Bearer 토큰
     * 2순위: 쿠키의 accessToken 값
     *
     * @param request HTTP 요청 객체
     * @return JWT 토큰 문자열 (없으면 null)
     */
    private String resolveToken(HttpServletRequest request) {
        // 1. Authorization 헤더에서 Bearer 토큰 추출
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 제거
        }

        // 2. 쿠키에서 accessToken 추출
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}
