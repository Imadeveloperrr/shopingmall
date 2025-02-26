package com.example.crud.security;

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
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            response.sendRedirect("/login");
        } else {
            // 다른 HTTP 메서드 (예: POST, PUT 등)는 JSON 에러 메시지를 반환
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\": \"아이디 또는 비밀번호가 다릅니다.\"}");
        }
    }
}
