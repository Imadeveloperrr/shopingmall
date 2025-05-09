package com.example.crud.controller;

import com.example.crud.common.security.JwtToken;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.token.TokenRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final MemberService memberService;

    @RequestMapping("/reissue")
    public ResponseEntity<JwtToken> reissue(@RequestBody TokenRequestDto tokenRequestDto) {
        JwtToken jwtToken = memberService.reissue(tokenRequestDto);
        return ResponseEntity.ok(jwtToken);
    }
}
