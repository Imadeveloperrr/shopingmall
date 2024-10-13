package com.example.crud.data.member.service;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.token.TokenRequestDto;
import com.example.crud.security.JwtToken;

public interface MemberService {
    JwtToken signIn(String username, String password, boolean rememberMe);

    MemberResponseDto signUp(MemberDto memberDto);

    MemberResponseDto getMember();

    MemberResponseDto updateMemBer(MemberDto memberDto);

    JwtToken reissue(TokenRequestDto tokenRequestDto); // Token 재발급
}
