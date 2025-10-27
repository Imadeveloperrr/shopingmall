package com.example.crud.data.member.service.signup;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;

/**
 * 회원 가입 서비스 인터페이스
 * - 확장 가능성: 일반 가입, 소셜 로그인 가입 등
 */
public interface MemberSignUpService {

    /**
     * 회원가입
     */
    MemberResponseDto signUp(MemberDto memberDto);
}
