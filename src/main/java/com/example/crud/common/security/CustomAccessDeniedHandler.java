package com.example.crud.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Spring Security 접근 거부 핸들러
 * 인증된 사용자가 충분한 권한이 없어 접근이 거부될 때 호출됨
 * 403 Forbidden 상태 코드와 함께 에러 메시지를 JSON 형식으로 제공
 */
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    /**
     * 접근 거부 시 호출되는 메서드
     * 인증은 성공했지만 해당 리소스에 접근할 권한이 없는 경우
     *
     * @param request HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param accessDeniedException 접근 거부 예외 정보
     */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {

        String requestURI = request.getRequestURI();

        // API 요청 또는 AJAX 요청인 경우: JSON 에러 응답
        if (requestURI.startsWith("/api/") ||
            "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                "{\"error\": \"FORBIDDEN\", \"message\": \"접근 권한이 없습니다.\"}");
        } else {
            // 일반 웹 페이지 요청: 에러 페이지로 리다이렉트
            response.sendRedirect("/error/403");
        }
    }
}
