package com.example.crud.controller;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.security.JwtToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

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

    @PostMapping(value = "/login")
    @ResponseBody
    public JwtToken loginPost(@RequestBody MemberDto memberDto, HttpServletResponse response, Model model) {
        String email = memberDto.getEmail();
        String password = memberDto.getPassword();
        JwtToken jwtToken = memberService.signIn(email, password);
        log.info("request email = {}, password = {}", email, password);
        log.info("JwtToken accessToken = {}, refreshToken = {}", jwtToken.getAccessToken(), jwtToken.getRefreshToken());

        Cookie refreshCookie = new Cookie("refreshToken", jwtToken.getRefreshToken());
        refreshCookie.setHttpOnly(true);
        response.addCookie(refreshCookie); // refreshToken은 쿠키에 저장

        Cookie accessToken = new Cookie("accessToken", jwtToken.getAccessToken());
        accessToken.setHttpOnly(true);
        response.addCookie(accessToken);

        return new JwtToken(jwtToken.getGrantType(), jwtToken.getAccessToken(), jwtToken.getRefreshToken());
    }



    @GetMapping("/register")
    public String register() {
        return "fragments/register";
    }

    @PostMapping("/register") //
    @ResponseBody
    public ResponseEntity<?> registerPost(@RequestBody MemberDto memberDto) { // AJAX 요청은 ResponseEntity 객체가 GOOD.
        MemberResponseDto memberResponseDto = memberService.signUp(memberDto);
        return ResponseEntity.ok().body(memberResponseDto); // HTTP 상태 코드와 응답 본문 설정
    }

}
