package com.example.crud.controller;

import com.example.crud.entity.Member;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class IndexController {


    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "fragments/login";
    }

    @GetMapping("/register")
    public String register() {
        return "fragments/register";
    }

    @PostMapping("/register") //
    public ResponseEntity<?> registerPost(@RequestBody Member member) { // AJAX 요청은 ResponseEntity 객체가 GOOD.
        return ResponseEntity.ok().body("Sign up Success."); // HTTP 상태 코드와 응답 본문 설정
        // redirect:/index
    }
}
