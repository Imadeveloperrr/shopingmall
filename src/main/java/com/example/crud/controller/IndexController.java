package com.example.crud.controller;

import com.example.crud.data.exception.ErrorResponse;
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
    public ResponseEntity<?> loginPost(@RequestBody MemberDto memberDto, HttpServletResponse response, Model model) {
        try {
            String email = memberDto.getEmail();
            String password = memberDto.getPassword();
            boolean rememberMe = memberDto.isRememberMe();

            JwtToken jwtToken = memberService.signIn(email, password, rememberMe);

            log.info("로그인 성공 : email = {}, rememberMe = {}", email, rememberMe);

            Cookie refreshCookie = new Cookie("refreshToken", jwtToken.getRefreshToken());
            refreshCookie.setHttpOnly(true);
            if (rememberMe)
                refreshCookie.setMaxAge(60 * 60 * 24 * 7); // 로그인유지 체크 했을시 7일간 유지
            response.addCookie(refreshCookie); // refreshToken은 쿠키에 저장

            Cookie accessToken = new Cookie("accessToken", jwtToken.getAccessToken());
            accessToken.setHttpOnly(true);
            if (rememberMe)
                refreshCookie.setMaxAge(60 * 60 * 24 * 7); // 로그인유지 체크 했을시 7일간 유지
            response.addCookie(accessToken);

            //return new JwtToken(jwtToken.getGrantType(), jwtToken.getAccessToken(), jwtToken.getRefreshToken());
            return ResponseEntity.ok().body("로그인 성공");
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "아이디 또는 비밀번호가 다릅니다."));
        } catch (Exception e) {
            log.error("로그인 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/register")
    public String register() {
        return "fragments/register";
    }

    @PostMapping("/register") //
    @ResponseBody
    public ResponseEntity<?> registerPost(@Valid @RequestBody MemberDto memberDto, BindingResult bindingResult) { // AJAX 요청은 ResponseEntity 객체가 GOOD.
        if(bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), errorMessage));
        }
        MemberResponseDto memberResponseDto = memberService.signUp(memberDto);
        return ResponseEntity.ok().body(memberResponseDto); // HTTP 상태 코드와 응답 본문 설정
    }

}
