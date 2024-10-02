package com.example.crud.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Custom Filter
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        try {
            // 1. Request Header에서 JWT 토큰 추출
            String token = resolveToken(request);

            // 2. validateToken으로 토큰 유효성 검사.
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired JWT token\"}");
        }

        filterChain.doFilter(request, response); // 다음 필터로 요청을 전달
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization"); // Authorization 헤더 추출

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer")) { // Bearer 접두사로 시작하는 토큰
            return bearerToken.substring(7);
        }

        Cookie[] cookies = request.getCookies(); // 헤더에 없다면 쿠키에서 토큰 확인
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
