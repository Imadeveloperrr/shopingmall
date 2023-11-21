package com.example.crud.controller;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.entity.Member;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class IndexController {

    private MemberService memberService;

    @Autowired
    public IndexController(MemberService memberService) {
        this.memberService = memberService;
    }

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
    public ResponseEntity<?> registerPost(@RequestBody MemberDto memberDto) { // AJAX 요청은 ResponseEntity 객체가 GOOD.
        MemberResponseDto memberResponseDto = memberService.signUp(memberDto);
        return ResponseEntity.ok().body(memberResponseDto); // HTTP 상태 코드와 응답 본문 설정
    }
}
