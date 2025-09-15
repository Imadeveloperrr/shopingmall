package com.example.crud.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/*
인증되지 않은 사용자가 보호된 리소스에 접근하려고 할때 호출됨.
401 Unaauthorized 상태 코드를 반환, 에러 메시지를 JSON 형식으로 제공.
 */
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        String requestURI = request.getRequestURI();
        
        // API 요청이나 특정 경로는 JSON 응답
        if (requestURI.startsWith("/api/") || 
            requestURI.startsWith("/actuator/") ||
            "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\": \"인증이 필요합니다.\"}");
        } else {
            // 일반 웹 페이지 요청은 로그인 페이지로 리다이렉트
            response.sendRedirect("/login");
        }
    }
}
