package com.example.crud.data.member.service;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.token.TokenRequestDto;
import com.example.crud.security.JwtToken;
import org.springframework.transaction.annotation.Transactional;

public interface MemberService {
    JwtToken signIn(String username, String password, boolean rememberMe);

    MemberResponseDto signUp(MemberDto memberDto);

    MemberResponseDto getMember();

    JwtToken reissue(TokenRequestDto tokenRequestDto); // Token 재발급

    MemberResponseDto updateMember(MemberDto memberDto);
}
