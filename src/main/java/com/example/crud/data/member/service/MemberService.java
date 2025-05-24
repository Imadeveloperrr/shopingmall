package com.example.crud.data.member.service;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.token.TokenRequestDto;
import com.example.crud.common.security.JwtToken;
import com.example.crud.entity.Member;

public interface MemberService {
    JwtToken signIn(String username, String password, boolean rememberMe);

    MemberResponseDto signUp(MemberDto memberDto);

    MemberResponseDto getMember();

    JwtToken reissue(TokenRequestDto tokenRequestDto); // Token 재발급

    MemberResponseDto updateMember(MemberDto memberDto);

    Member getMemberEntity(String name);
}
