package com.example.crud.controller;

import com.example.crud.data.member.dto.request.UpdateProfileRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.data.member.service.find.MemberFindService;
import com.example.crud.data.member.service.profile.UpdateMemberProfileService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private static final Logger log = LoggerFactory.getLogger(MyPageController.class);
    private final MemberFindService memberFindService;
    private final UpdateMemberProfileService updateMemberProfileService;
    private final ProductService productService;

    @GetMapping
    public String mypage(Model model) {
        MemberResponse member = memberFindService.getCurrentMember();
        model.addAttribute("member", member);

        List<ProductResponseDto> productResponseDto = productService.getMyProducts();
        model.addAttribute("products", productResponseDto);
        return "fragments/mypage";
    }

    @GetMapping("/profileEdit")
    public String getProfileEdit(Model model) {
        MemberResponse member = memberFindService.getCurrentMember();
        model.addAttribute("member", member);
        return "fragments/profileEdit";
    }

    @PostMapping("/profileEdit")
    public ResponseEntity<?> postProfileEdit(@RequestBody UpdateProfileRequest request) {
        try {
            log.info("프로필 업데이트 : {}", request);
            MemberResponse response = updateMemberProfileService.updateMember(request);
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
