package com.example.crud.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Spring Security 인증 실패 엔트리 포인트
 * 인증되지 않은 사용자가 보호된 리소스에 접근 시 호출됨
 * API 요청과 일반 웹 요청을 구분하여 각각 다른 응답 제공
 */
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * 인증 실패 시 호출되는 메서드
     * 요청 타입에 따라 다른 응답 방식을 제공
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param authException 인증 예외 정보
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String requestURI = request.getRequestURI();
        // API 요청 또는 AJAX 요청인 경우: JSON 에러 응답
        if (requestURI.startsWith("/api/") ||
            requestURI.startsWith("/actuator/") ||
            "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"error\": \"UNAUTHORIZED\", \"message\": \"인증이 필요합니다.\"}");
        } else {
            // 일반 웹 페이지 요청: 로그인 페이지로 리다이렉트
            response.sendRedirect("/login");
        }
    }
}
