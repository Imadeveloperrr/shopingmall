package com.example.crud.controller;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private static final Logger log = LoggerFactory.getLogger(MyPageController.class);
    private final MemberService memberService;
    private final ProductService productService;

   @GetMapping
    public String mypage(Model model) {
        MemberResponseDto memberResponseDto = memberService.getMember();
        model.addAttribute("member", memberResponseDto);

        List<ProductResponseDto> productResponseDto = productService.getMyProducts();
        model.addAttribute("products", productResponseDto);
        return "fragments/mypage";
    }

    @GetMapping("/profileEdit")
    public String getProfileEdit(Model model) {
        MemberResponseDto memberResponseDto = memberService.getMember();
        model.addAttribute("member", memberResponseDto);
        return "fragments/profileEdit";
    }

    @PostMapping("/profileEdit")
    public ResponseEntity<?> postProfileEdit(@RequestBody MemberDto memberDto) {
        try {
            log.info("프로필 업데이트 : {}", memberDto);
            MemberResponseDto memberResponseDto = memberService.updateMemBer(memberDto);
            return ResponseEntity.ok().body(memberResponseDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
