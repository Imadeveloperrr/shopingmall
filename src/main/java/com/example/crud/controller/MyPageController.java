package com.example.crud.controller;

import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mypage")
public class MyPageController {

    private final MemberService memberService;

    @Autowired
    public MyPageController(MemberService memberService) {
        this.memberService = memberService;
    }
    @GetMapping
    public String mypage(Model model) {
        MemberResponseDto memberResponseDto = memberService.getMember();
        model.addAttribute("member", memberResponseDto);
        return "fragments/mypage";
    }

    @GetMapping("/profileEdit")
    public String profileEdit(Model model) {
        MemberResponseDto memberResponseDto = memberService.getMember();
        model.addAttribute("member", memberResponseDto);
        return "fragments/profileEdit";
    }
}
