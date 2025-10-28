package com.example.crud.controller;

import com.example.crud.common.exception.ValidationException;
import com.example.crud.common.security.JwtToken;
import com.example.crud.data.member.dto.request.LoginRequest;
import com.example.crud.data.member.dto.request.SignUpRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.data.member.service.auth.MemberAuthService;
import com.example.crud.data.member.service.signup.MemberSignUpService;
import com.example.crud.data.token.TokenRequestDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final MemberAuthService memberAuthService;
    private final MemberSignUpService memberSignUpService;

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> login(
            @RequestBody @Valid LoginRequest request,
            HttpServletResponse response) {

        JwtToken jwtToken = memberAuthService.signIn(
                request.getEmail(),
                request.getPassword(),
                request.isRememberMe()
        );

        setCookie(response, "accessToken", jwtToken.getAccessToken(),
                request.isRememberMe() ? 60 * 60 * 24 * 7 : -1);
        setCookie(response, "refreshToken", jwtToken.getRefreshToken(),
                request.isRememberMe() ? 60 * 60 * 24 * 14 : -1);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "로그인 성공");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemberResponse> register(
            @Valid @RequestBody SignUpRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            throw new ValidationException(bindingResult.getAllErrors());
        }

        return ResponseEntity.ok(memberSignUpService.signUp(request));
    }

    @PostMapping("/auth/reissue")
    public ResponseEntity<JwtToken> reissue(@RequestBody TokenRequestDto tokenRequestDto) {
        JwtToken jwtToken = memberAuthService.reissue(tokenRequestDto);
        return ResponseEntity.ok(jwtToken);
    }

    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
