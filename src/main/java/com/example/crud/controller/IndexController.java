package com.example.crud.controller;

import com.example.crud.common.exception.ValidationException;
import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.common.security.JwtToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IndexController {
    private final MemberService memberService;
    private final ProductService productService;

    @GetMapping("/")
    public String index(Model model) {
        List<ProductResponseDto> products = productService.getProducts();
        model.addAttribute("products", products);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "fragments/login";
    }

    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<Map<String, String>> login(
            @RequestBody @Valid MemberDto memberDto,
            HttpServletResponse response) {

        JwtToken jwtToken = memberService.signIn(
                memberDto.getEmail(),
                memberDto.getPassword(),
                memberDto.isRememberMe()
        );

        // 토큰을 쿠키에 저장
        setCookie(response, "accessToken", jwtToken.getAccessToken(),
                memberDto.isRememberMe() ? 60 * 60 * 24 * 7 : -1);
        setCookie(response, "refreshToken", jwtToken.getRefreshToken(),
                memberDto.isRememberMe() ? 60 * 60 * 24 * 14 : -1);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "로그인 성공");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    @GetMapping("/register")
    public String register() {
        return "fragments/register";
    }

    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<MemberResponseDto> register(
            @Valid @RequestBody MemberDto memberDto,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            throw new ValidationException(bindingResult.getAllErrors());
        }

        return ResponseEntity.ok(memberService.signUp(memberDto));
    }
}
